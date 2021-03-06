// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.ScalableIcon;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.icons.CopyableIcon;
import com.intellij.ui.icons.RowIcon;
import com.intellij.ui.scale.ScaleType;
import com.intellij.ui.tabs.impl.TabLabel;
import com.intellij.util.Alarm;
import com.intellij.util.Function;
import com.intellij.util.IconUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBScalableIcon;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;

public final class DeferredIconImpl<T> extends JBScalableIcon implements DeferredIcon, RetrievableIcon, IconWithToolTip, CopyableIcon {
  private static final Logger LOG = Logger.getInstance(DeferredIconImpl.class);
  private static final int MIN_AUTO_UPDATE_MILLIS = 950;
  private static final RepaintScheduler ourRepaintScheduler = new RepaintScheduler();

  private final @NotNull Icon myDelegateIcon;

  private volatile @NotNull Icon myScaledDelegateIcon;
  private DeferredIconImpl<T> myScaledIconCache;

  private java.util.function.Function<? super T, ? extends Icon> myEvaluator;
  private volatile boolean myIsScheduled;
  private final T myParam;
  private static final Icon EMPTY_ICON = EmptyIcon.create(16).withIconPreScaled(false);
  private final boolean myNeedReadAction;
  private boolean myDone;
  private final boolean myAutoUpdatable;
  private long myLastCalcTime;
  private long myLastTimeSpent;

  private static final ExecutorService ourIconCalculatingExecutor =
    AppExecutorUtil.createBoundedApplicationPoolExecutor("IconCalculating Pool", 1);

  private final BiConsumer<DeferredIcon, Icon> myEvalListener;

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
    myAutoUpdatable = icon.myAutoUpdatable;
    myLastCalcTime = icon.myLastCalcTime;
    myLastTimeSpent = icon.myLastTimeSpent;
    myEvalListener = icon.myEvalListener;
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

  DeferredIconImpl(Icon baseIcon,
                   T param,
                   @NotNull java.util.function.Function<? super T, ? extends Icon> evaluator,
                   @NotNull BiConsumer<DeferredIcon, Icon> listener,
                   boolean autoUpdatable) {
    this(baseIcon, param, true, evaluator, listener, autoUpdatable);
  }

  public static <T> @NotNull DeferredIcon withoutReadAction(Icon baseIcon, T param, @NotNull java.util.function.Function<? super T, ? extends Icon> evaluator) {
    return new DeferredIconImpl<>(baseIcon, param, false, evaluator, null, false);
  }

  public DeferredIconImpl(Icon baseIcon, T param, final boolean needReadAction, @NotNull Function<? super T, ? extends Icon> evaluator) {
    this(baseIcon, param, needReadAction, t -> evaluator.fun(t), null, false);
  }

  private DeferredIconImpl(Icon baseIcon,
                           T param,
                           boolean needReadAction,
                           @NotNull java.util.function.Function<? super T, ? extends Icon> evaluator,
                           @Nullable BiConsumer<DeferredIcon, Icon> listener,
                           boolean autoUpdatable) {
    myParam = param;
    myDelegateIcon = nonNull(baseIcon);
    myScaledDelegateIcon = myDelegateIcon;
    myScaledIconCache = null;
    myEvaluator = evaluator;
    myNeedReadAction = needReadAction;
    myEvalListener = listener;
    myAutoUpdatable = autoUpdatable;
    checkDelegationDepth();
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

    if (isDone() || myIsScheduled || PowerSaveMode.isEnabled()) {
      return;
    }
    scheduleEvaluation(c, x, y);
  }

  @VisibleForTesting
  Future<?> scheduleEvaluation(Component c, int x, int y) {
    myIsScheduled = true;

    final Component target = getTarget(c);
    final Component paintingParent = SwingUtilities.getAncestorOfClass(PaintingParent.class, c);
    final Rectangle paintingParentRec = paintingParent == null ? null : ((PaintingParent)paintingParent).getChildRec(c);
    return ourIconCalculatingExecutor.submit(() -> {
      int oldWidth = myScaledDelegateIcon.getIconWidth();
      final Icon[] evaluated = new Icon[1];

      final long startTime = System.currentTimeMillis();
      if (myNeedReadAction) {
        boolean result = ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(() -> {
          IconDeferrerImpl.evaluateDeferred(() -> evaluated[0] = evaluate());
          if (myAutoUpdatable) {
            myLastCalcTime = System.currentTimeMillis();
            myLastTimeSpent = myLastCalcTime - startTime;
          }
        });
        if (!result) {
          myIsScheduled = false;
          return;
        }
      }
      else {
        IconDeferrerImpl.evaluateDeferred(() -> evaluated[0] = evaluate());
        if (myAutoUpdatable) {
          myLastCalcTime = System.currentTimeMillis();
          myLastTimeSpent = myLastCalcTime - startTime;
        }
      }
      final Icon result = evaluated[0];
      myScaledDelegateIcon = result;
      checkDelegationDepth();

      boolean shouldRevalidate = Registry.is("ide.tree.deferred.icon.invalidates.cache") && myScaledDelegateIcon.getIconWidth() != oldWidth;
      ApplicationManager.getApplication().invokeLater(() -> {
        setDone(result);
        if (equalIcons(result, myDelegateIcon)) {
          return;
        }

        Component actualTarget = target;
        if (actualTarget != null && SwingUtilities.getWindowAncestor(actualTarget) == null) {
          actualTarget = paintingParent;
          if (actualTarget == null || SwingUtilities.getWindowAncestor(actualTarget) == null) {
            actualTarget = null;
          }
        }

        if (actualTarget == null) {
          return;
        }

        // revalidate will not work: JTree caches size of nodes
        if (shouldRevalidate && actualTarget instanceof JTree) {
          TreeUtil.invalidateCacheAndRepaint(((JTree)actualTarget).getUI());
        }

        if (c == actualTarget) {
          c.repaint(x, y, getIconWidth(), getIconHeight());
        }
        else {
          ourRepaintScheduler.pushDirtyComponent(actualTarget, paintingParentRec);
        }
      }, ModalityState.any());
    });
  }

  private static Component getTarget(Component c) {
    final Component target;

    final Container list = SwingUtilities.getAncestorOfClass(JList.class, c);
    if (list != null) {
      target = list;
    }
    else {
      final Container tree = SwingUtilities.getAncestorOfClass(JTree.class, c);
      if (tree != null) {
        target = tree;
      }
      else {
        final Container table = SwingUtilities.getAncestorOfClass(JTable.class, c);
        if (table != null) {
          target = table;
        }
        else {
          final Container box = SwingUtilities.getAncestorOfClass(JComboBox.class, c);
          if (box != null) {
            target = box;
          }
          else {
            final Container tabLabel = SwingUtilities.getAncestorOfClass(TabLabel.class, c);
            target = tabLabel == null ? c : tabLabel;
          }
        }
      }
    }
    return target;
  }

  private void setDone(@NotNull Icon result) {
    if (myEvalListener != null) {
      myEvalListener.accept(this, result);
    }

    myDone = true;
    if (!myAutoUpdatable) {
      myEvaluator = null;
    }
  }

  @NotNull
  @Override
  public Icon retrieveIcon() {
    return isDone() ? myScaledDelegateIcon : evaluate();
  }

  public boolean isNeedReadAction() {
    return myNeedReadAction;
  }

  @NotNull
  @Override
  public Icon evaluate() {
    Icon result;
    try {
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
    if (myAutoUpdatable && myDone && myLastCalcTime > 0 && System.currentTimeMillis() - myLastCalcTime > Math.max(MIN_AUTO_UPDATE_MILLIS, 10 * myLastTimeSpent)) {
      myDone = false;
      myIsScheduled = false;
    }
    return myDone;
  }

  private static final class RepaintScheduler {
    private final Alarm myAlarm = new Alarm();
    private final Set<RepaintRequest> myQueue = new LinkedHashSet<>();

    private void pushDirtyComponent(@NotNull Component c, final Rectangle rec) {
      ApplicationManager.getApplication().assertIsDispatchThread(); // assert myQueue accessed from EDT only
      myAlarm.cancelAllRequests();
      myAlarm.addRequest(() -> {
        for (RepaintRequest each : myQueue) {
          Rectangle r = each.rectangle;
          if (r == null) {
            each.component.repaint();
          }
          else {
            each.component.repaint(r.x, r.y, r.width, r.height);
          }
        }
        myQueue.clear();
      }, 50);

      myQueue.add(new RepaintRequest(c, rec));
    }
  }

  private static final class RepaintRequest {
    final Component component;
    final Rectangle rectangle;

    private RepaintRequest(@NotNull Component component, @Nullable Rectangle rectangle) {
      this.component = component;
      this.rectangle = rectangle;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      RepaintRequest request = (RepaintRequest)o;
      return component.equals(request.component) && Objects.equals(rectangle, request.rectangle);
    }

    @Override
    public int hashCode() {
      int result = component.hashCode();
      result = 31 * result + (rectangle != null ? rectangle.hashCode() : 0);
      return result;
    }
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
}
