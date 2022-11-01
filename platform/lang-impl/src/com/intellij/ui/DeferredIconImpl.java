// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.codeWithMe.ClientId;
import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.ScalableIcon;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.icons.CopyableIcon;
import com.intellij.ui.icons.ReplaceableIcon;
import com.intellij.ui.icons.RowIcon;
import com.intellij.ui.scale.ScaleType;
import com.intellij.util.Function;
import com.intellij.util.IconUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.EdtScheduledExecutorService;
import com.intellij.util.ui.EDT;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBScalableIcon;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

public final class DeferredIconImpl<T> extends JBScalableIcon implements DeferredIcon, RetrievableIcon, IconWithToolTip, CopyableIcon {
  private static final Logger LOG = Logger.getInstance(DeferredIconImpl.class);
  private static final int MIN_AUTO_UPDATE_MILLIS = 950;
  private static final DeferredIconRepaintScheduler ourRepaintScheduler = new DeferredIconRepaintScheduler();

  private final @NotNull Icon myDelegateIcon;

  private volatile @NotNull Icon myScaledDelegateIcon;
  private DeferredIconImpl<T> myScaledIconCache;

  private java.util.function.Function<? super T, ? extends Icon> myEvaluator;
  private Set<DeferredIconRepaintScheduler.RepaintRequest> myScheduledRepaints;
  private volatile boolean myIsScheduled;
  final T myParam;
  private static final Icon EMPTY_ICON = EmptyIcon.create(16).withIconPreScaled(false);
  private final boolean myNeedReadAction;
  private boolean myDone;
  private long myLastCalcTime;
  private long myLastTimeSpent;

  private AtomicLong myModificationCount = new AtomicLong(0);

  private static final ExecutorService ourIconCalculatingExecutor =
    AppExecutorUtil.createBoundedApplicationPoolExecutor("IconCalculating Pool", 1);

  private final @Nullable BiConsumer<? super DeferredIcon, ? super Icon> myEvalListener;

  private DeferredIconImpl(@NotNull DeferredIconImpl<T> icon) {
    super(icon);
    myDelegateIcon = icon.myDelegateIcon;
    myScaledDelegateIcon = icon.myDelegateIcon;
    myScaledIconCache = null;
    myEvaluator = icon.myEvaluator;
    myIsScheduled = icon.myIsScheduled;
    myParam = icon.myParam;
    myNeedReadAction = icon.myNeedReadAction;
    myDone = icon.myDone;
    myLastCalcTime = icon.myLastCalcTime;
    myLastTimeSpent = icon.myLastTimeSpent;
    myEvalListener = icon.myEvalListener;
    myModificationCount = icon.myModificationCount;
  }

  DeferredIconImpl(Icon baseIcon,
                   T param,
                   boolean needReadAction,
                   @NotNull java.util.function.Function<? super T, ? extends Icon> evaluator,
                   @Nullable BiConsumer<? super DeferredIcon, ? super Icon> listener) {
    myParam = param;
    myDelegateIcon = nonNull(baseIcon);
    myScaledDelegateIcon = myDelegateIcon;
    myScaledIconCache = null;
    myEvaluator = evaluator;
    myNeedReadAction = needReadAction;
    myEvalListener = listener;
    checkDelegationDepth();
  }

  public DeferredIconImpl(Icon baseIcon, T param, final boolean needReadAction, @NotNull Function<? super T, ? extends Icon> evaluator) {
    this(baseIcon, param, needReadAction, t -> evaluator.fun(t), null);
  }

  @Override
  public long getModificationCount() {
    return myModificationCount.get();
  }

  @NotNull
  @Override
  public Icon replaceBy(@NotNull IconReplacer replacer) {
    return new DeferredIconAfterReplace<>(this, replacer);
  }

  @Override
  public @NotNull DeferredIconImpl<T> copy() {
    return new DeferredIconImpl<>(this);
  }

  @NotNull
  @Override
  public DeferredIconImpl<T> scale(float scale) {
    if (getScale() == scale) {
      return this;
    }

    DeferredIconImpl<T> icon = myScaledIconCache;
    if (icon == null || icon.getScale() != scale) {
      icon = new DeferredIconImpl<>(this);
      icon.setScale(ScaleType.OBJ_SCALE.of(scale));
      myScaledIconCache = icon;
    }
    icon.myScaledDelegateIcon = IconUtil.scale(icon.myDelegateIcon, null, scale);
    return icon;
  }

  public static <T> @NotNull DeferredIcon withoutReadAction(Icon baseIcon, T param, @NotNull java.util.function.Function<? super T, ? extends Icon> evaluator) {
    return new DeferredIconImpl<>(baseIcon, param, false, evaluator, null);
  }

  @NotNull
  @Override
  public Icon getBaseIcon() {
    return myDelegateIcon;
  }

  private void checkDelegationDepth() {
    int depth = 0;
    DeferredIconImpl<?> each = this;
    while (each.myScaledDelegateIcon instanceof DeferredIconImpl && depth < 50) {
      depth++;
      each = (DeferredIconImpl<?>)each.myScaledDelegateIcon;
    }
    if (depth >= 50) {
      LOG.error("Too deep deferred icon nesting");
    }
  }

  @NotNull
  private static Icon nonNull(@Nullable Icon icon) {
    return icon == null ? EMPTY_ICON : icon;
  }

  @Override
  public void paintIcon(Component c, @NotNull Graphics g, int x, int y) {
    Icon scaledDelegateIcon = myScaledDelegateIcon;
    if (!(scaledDelegateIcon instanceof DeferredIconImpl &&
          ((DeferredIconImpl<?>)scaledDelegateIcon).myScaledDelegateIcon instanceof DeferredIconImpl)) {
      //SOE protection
      scaledDelegateIcon.paintIcon(c, g, x, y);
    }

    if (needScheduleEvaluation()) {
      scheduleEvaluation(c, x, y);
    }
  }

  @Override
  public void notifyPaint(@NotNull Component c, int x, int y) {
    if (needScheduleEvaluation()) {
      scheduleEvaluation(c, x, y);
    }
  }

  private boolean needScheduleEvaluation() {
    if (isDone() || PowerSaveMode.isEnabled()) {
      return false;
    }
    return true;
  }

  @VisibleForTesting
  Future<?> scheduleEvaluation(Component c, int x, int y) {
    // It is important to extract the repaint target here:
    // the component may be a temporary component used by some list or tree to paint elements
    DeferredIconRepaintScheduler.RepaintRequest repaintRequest = ourRepaintScheduler.createRepaintRequest(c, x, y);
    AppUIUtil.invokeOnEdt(() -> {
      if (isDone()) {
        return;
      }
      if (myScheduledRepaints == null) {
        myScheduledRepaints = Collections.singleton(repaintRequest);
      }
      else {
        if (!myScheduledRepaints.contains(repaintRequest)) {
          if (myScheduledRepaints.size() == 1) {
            myScheduledRepaints = new HashSet<>(myScheduledRepaints);
          }
          myScheduledRepaints.add(repaintRequest);
        }
      }
    });

    if (myIsScheduled) {
      return null;
    }

    myIsScheduled = true;

    return ourIconCalculatingExecutor.submit(() -> {
      int oldWidth = myScaledDelegateIcon.getIconWidth();
      final Icon[] evaluated = new Icon[1];

      boolean success = true;
      if (myNeedReadAction) {
        success = ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(() -> {
          IconDeferrerImpl.evaluateDeferred(() -> evaluated[0] = evaluate());
        });
      }
      else {
        IconDeferrerImpl.evaluateDeferred(() -> evaluated[0] = evaluate());
      }
      final Icon result = evaluated[0];
      if (!success || result == null) {
        myIsScheduled = false;
        EdtScheduledExecutorService.getInstance().schedule(() -> {
          if (needScheduleEvaluation()) {
            scheduleEvaluation(c, x, y);
          }
        }, MIN_AUTO_UPDATE_MILLIS, TimeUnit.MILLISECONDS);
        return;
      }

      myScaledDelegateIcon = result;
      myModificationCount.incrementAndGet();
      checkDelegationDepth();

      processRepaints(oldWidth, result);
    });
  }

  private void processRepaints(int oldWidth, Icon result) {
    boolean shouldRevalidate = Registry.is("ide.tree.deferred.icon.invalidates.cache") && myScaledDelegateIcon.getIconWidth() != oldWidth;
    ApplicationManager.getApplication().invokeLater(() -> {
      Set<DeferredIconRepaintScheduler.RepaintRequest> repaints = myScheduledRepaints;
      setDone(result);
      if (equalIcons(result, myDelegateIcon)) {
        return;
      }
      for (DeferredIconRepaintScheduler.RepaintRequest repaintRequest : repaints) {
        Component actualTarget = repaintRequest.getActualTarget();
        if (actualTarget == null) {
          continue;
        }

        // revalidate will not work: JTree caches size of nodes
        if (shouldRevalidate && actualTarget instanceof JTree) {
          TreeUtil.invalidateCacheAndRepaint(((JTree)actualTarget).getUI());
        }

        ourRepaintScheduler.scheduleRepaint(repaintRequest, getIconWidth(), getIconHeight(), false);
      }
    }, ModalityState.any());
  }

  private void setDone(@NotNull Icon result) {
    if (myEvalListener != null) {
      myEvalListener.accept(this, result);
    }

    myDone = true;
    myEvaluator = null;
    myScheduledRepaints = null;
  }

  @NotNull
  @Override
  public Icon retrieveIcon() {
    if (isDone()) {
      return myScaledDelegateIcon;
    }
    if (EDT.isCurrentThreadEdt()) {
      return myScaledDelegateIcon;
    }
    return evaluate();
  }

  public boolean isNeedReadAction() {
    return myNeedReadAction;
  }

  @NotNull
  @Override
  public Icon evaluate() {
    Icon result;
    // Icon evaluation is not something that should be related to any client
    try (AccessToken ignored = ClientId.withClientId((ClientId)null)) {
      result = nonNull(myEvaluator.apply(myParam));
    }
    catch (IndexNotReadyException e) {
      result = EMPTY_ICON;
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      checkDoesntReferenceThis(result);
    }

    if (getScale() != 1f && result instanceof ScalableIcon) {
      result = ((ScalableIcon)result).scale(getScale());
    }
    return result;
  }

  private void checkDoesntReferenceThis(final Icon icon) {
    if (icon == this) {
      throw new IllegalStateException("Loop in icons delegation");
    }

    if (icon instanceof DeferredIconImpl) {
      checkDoesntReferenceThis(((DeferredIconImpl<?>)icon).myScaledDelegateIcon);
    }
    else if (icon instanceof LayeredIcon) {
      for (Icon layer : ((LayeredIcon)icon).getAllLayers()) {
        checkDoesntReferenceThis(layer);
      }
    }
    else if (icon instanceof com.intellij.ui.icons.RowIcon) {
      final com.intellij.ui.icons.RowIcon rowIcon = (RowIcon)icon;
      final int count = rowIcon.getIconCount();
      for (int i = 0; i < count; i++) {
        checkDoesntReferenceThis(rowIcon.getIcon(i));
      }
    }
  }

  @Override
  public int getIconWidth() {
    return myScaledDelegateIcon.getIconWidth();
  }

  @Override
  public int getIconHeight() {
    return myScaledDelegateIcon.getIconHeight();
  }

  @Override
  public String getToolTip(boolean composite) {
    if (myScaledDelegateIcon instanceof IconWithToolTip) {
      return ((IconWithToolTip) myScaledDelegateIcon).getToolTip(composite);
    }
    return null;
  }

  public boolean isDone() {
    return myDone;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof DeferredIconImpl && paramsEqual(this, (DeferredIconImpl<?>)obj);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myParam, myScaledDelegateIcon);
  }

  static boolean equalIcons(Icon icon1, Icon icon2) {
    if (icon1 instanceof DeferredIconImpl && icon2 instanceof DeferredIconImpl) {
      return paramsEqual((DeferredIconImpl<?>)icon1, (DeferredIconImpl<?>)icon2);
    }
    return Objects.equals(icon1, icon2);
  }

  private static boolean paramsEqual(@NotNull DeferredIconImpl<?> icon1, @NotNull DeferredIconImpl<?> icon2) {
    return Comparing.equal(icon1.myParam, icon2.myParam) &&
      equalIcons(icon1.myScaledDelegateIcon, icon2.myScaledDelegateIcon);
  }

  @Override
  public String toString() {
    return "Deferred. Base=" + myScaledDelegateIcon;
  }


  /**
   * Later it may be needed to implement more interfaces here. Ideally the same as in the DeferredIconImpl itself.
   */
  private static class DeferredIconAfterReplace<T> implements ReplaceableIcon {
    private final @NotNull DeferredIconImpl<T> myOriginal;
    private @NotNull Icon myOriginalEvaluatedIcon;
    private final @NotNull IconReplacer myReplacer;
    private @NotNull Icon myResultIcon;

    DeferredIconAfterReplace(@NotNull DeferredIconImpl<T> original, @NotNull IconReplacer replacer) {
      myOriginal = original;
      myOriginalEvaluatedIcon = myOriginal.myScaledDelegateIcon;
      myReplacer = replacer;
      myResultIcon = myReplacer.replaceIcon(myOriginalEvaluatedIcon);
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      if (myOriginal.needScheduleEvaluation()) {
        myOriginal.scheduleEvaluation(c, x, y);
      } else if (myOriginalEvaluatedIcon != myOriginal.myScaledDelegateIcon) {
        myOriginalEvaluatedIcon = myOriginal.myScaledDelegateIcon;
        myResultIcon = myReplacer.replaceIcon(myOriginalEvaluatedIcon);
      }
      myResultIcon.paintIcon(c, g, x, y);
    }

    @Override
    public int getIconWidth() {
      return myResultIcon.getIconWidth();
    }

    @Override
    public int getIconHeight() {
      return myResultIcon.getIconHeight();
    }

    @Override
    public @NotNull Icon replaceBy(@NotNull IconReplacer replacer) {
      return replacer.replaceIcon(myOriginal);
    }
  }
}
