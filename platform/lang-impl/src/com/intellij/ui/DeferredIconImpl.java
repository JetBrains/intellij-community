/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * @author max
 */
package com.intellij.ui;

import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.ScalableIcon;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.tabs.impl.TabLabel;
import com.intellij.util.Alarm;
import com.intellij.util.Function;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.TransferToEDTQueue;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.TreeUI;
import javax.swing.plaf.basic.BasicTreeUI;
import java.awt.*;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executor;

public class DeferredIconImpl<T> extends JBUI.CachingScalableJBIcon implements DeferredIcon, RetrievableIcon {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.DeferredIconImpl");
  private static final int MIN_AUTO_UPDATE_MILLIS = 950;
  private static final RepaintScheduler ourRepaintScheduler = new RepaintScheduler();
  @NotNull
  private final Icon myDelegateIcon;
  private volatile Icon myScaledDelegateIcon;
  private Function<T, Icon> myEvaluator;
  private volatile boolean myIsScheduled;
  private T myParam;
  private static final Icon EMPTY_ICON = JBUI.scale(EmptyIcon.create(16));
  private final boolean myNeedReadAction;
  private boolean myDone;
  private final boolean myAutoUpdatable;
  private long myLastCalcTime;
  private long myLastTimeSpent;

  private static final Executor ourIconsCalculatingExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("ourIconsCalculating pool",1);

  private final IconListener<T> myEvalListener;
  private static final TransferToEDTQueue<Runnable> ourLaterInvocator = TransferToEDTQueue.createRunnableMerger("Deferred icon later invocator", 200);

  protected DeferredIconImpl(DeferredIconImpl icon) {
    super(icon);
    myDelegateIcon = icon.myDelegateIcon;
    myScaledDelegateIcon = icon.myDelegateIcon;
    myEvaluator = icon.myEvaluator;
    myIsScheduled = icon.myIsScheduled;
    myParam = (T)icon.myParam;
    myNeedReadAction = icon.myNeedReadAction;
    myDone = icon.myDone;
    myAutoUpdatable = icon.myAutoUpdatable;
    myLastCalcTime = icon.myLastCalcTime;
    myLastTimeSpent = icon.myLastTimeSpent;
    myEvalListener = icon.myEvalListener;
  }

  @Override
  protected DeferredIconImpl copy() {
    return new DeferredIconImpl(this);
  }

  @Override
  public void setScale(float scale) {
    if (getScale() != scale && myDelegateIcon instanceof ScalableIcon) {
      myScaledDelegateIcon = ((ScalableIcon)myDelegateIcon).scale(scale);
      super.setScale(scale);
      return;
    }
  }

  private static class Holder {
    private static final boolean CHECK_CONSISTENCY = ApplicationManager.getApplication().isUnitTestMode();
  }

  public DeferredIconImpl(Icon baseIcon, T param, @NotNull Function<T, Icon> evaluator, @NotNull IconListener<T> listener, boolean autoUpdatable) {
    this(baseIcon, param, true, evaluator, listener, autoUpdatable);
  }

  public DeferredIconImpl(Icon baseIcon, T param, final boolean needReadAction, @NotNull Function<T, Icon> evaluator) {
    this(baseIcon, param, needReadAction, evaluator, null, false);
  }

  private DeferredIconImpl(Icon baseIcon, T param, boolean needReadAction, @NotNull Function<T, Icon> evaluator, @Nullable IconListener<T> listener, boolean autoUpdatable) {
    myParam = param;
    myDelegateIcon = nonNull(baseIcon);
    myScaledDelegateIcon = myDelegateIcon;
    myEvaluator = evaluator;
    myNeedReadAction = needReadAction;
    myEvalListener = listener;
    myAutoUpdatable = autoUpdatable;
    checkDelegationDepth();
  }

  private void checkDelegationDepth() {
    int depth = 0;
    DeferredIconImpl each = this;
    while (each.myScaledDelegateIcon instanceof DeferredIconImpl && depth < 50) {
      depth++;
      each = (DeferredIconImpl)each.myScaledDelegateIcon;
    }
    if (depth >= 50) {
      LOG.error("Too deep deferred icon nesting");
    }
  }

  @NotNull
  private static Icon nonNull(final Icon icon) {
    return icon == null ? EMPTY_ICON : icon;
  }

  @Override
  public void paintIcon(final Component c, @NotNull final Graphics g, final int x, final int y) {
    if (!(myScaledDelegateIcon instanceof DeferredIconImpl && ((DeferredIconImpl)myScaledDelegateIcon).myScaledDelegateIcon instanceof DeferredIconImpl)) {
      myScaledDelegateIcon.paintIcon(c, g, x, y); //SOE protection
    }

    if (isDone() || myIsScheduled || PowerSaveMode.isEnabled()) {
      return;
    }
    myIsScheduled = true;

    final Component target = getTarget(c);
    final Component paintingParent = SwingUtilities.getAncestorOfClass(PaintingParent.class, c);
    final Rectangle paintingParentRec = paintingParent == null ? null : ((PaintingParent)paintingParent).getChildRec(c);
    ourIconsCalculatingExecutor.execute(() -> {
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

      final boolean shouldRevalidate =
        Registry.is("ide.tree.deferred.icon.invalidates.cache") && myScaledDelegateIcon.getIconWidth() != oldWidth;

      ourLaterInvocator.offer(() -> {
        setDone(result);

        Component actualTarget = target;
        if (actualTarget != null && SwingUtilities.getWindowAncestor(actualTarget) == null) {
          actualTarget = paintingParent;
          if (actualTarget == null || SwingUtilities.getWindowAncestor(actualTarget) == null) {
            actualTarget = null;
          }
        }

        if (actualTarget == null) return;

        if (shouldRevalidate) {
          // revalidate will not work: JTree caches size of nodes
          if (actualTarget instanceof JTree) {
            final TreeUI ui = ((JTree)actualTarget).getUI();
            if (ui instanceof BasicTreeUI) {
              // this call is "fake" and only need to reset tree layout cache
              ((BasicTreeUI)ui).setLeftChildIndent(UIUtil.getTreeLeftChildIndent());
            }
          }
        }

        if (c == actualTarget) {
          c.repaint(x, y, getIconWidth(), getIconHeight());
        }
        else {
          ourRepaintScheduler.pushDirtyComponent(actualTarget, paintingParentRec);
        }
      });
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

  void setDone(@NotNull Icon result) {
    if (myEvalListener != null) {
      myEvalListener.evalDone(this, myParam, result);
    }

    myDone = true;
    if (!myAutoUpdatable) {
      myEvaluator = null;
      myParam = null;
    }
  }

  @Nullable
  @Override
  public Icon retrieveIcon() {
    return isDone() ? myScaledDelegateIcon : evaluate();
  }

  @NotNull
  @Override
  public Icon evaluate() {
    Icon result;
    try {
      result = nonNull(myEvaluator.fun(myParam));
    }
    catch (IndexNotReadyException e) {
      result = EMPTY_ICON;
    }

    if (Holder.CHECK_CONSISTENCY) {
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
      checkDoesntReferenceThis(((DeferredIconImpl)icon).myScaledDelegateIcon);
    }
    else if (icon instanceof LayeredIcon) {
      for (Icon layer : ((LayeredIcon)icon).getAllLayers()) {
        checkDoesntReferenceThis(layer);
      }
    }
    else if (icon instanceof RowIcon) {
      final RowIcon rowIcon = (RowIcon)icon;
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

  public boolean isDone() {
    if (myAutoUpdatable && myDone && myLastCalcTime > 0 && System.currentTimeMillis() - myLastCalcTime > Math.max(MIN_AUTO_UPDATE_MILLIS, 10 * myLastTimeSpent)) {
      myDone = false;
      myIsScheduled = false;
    }
    return myDone;
  }

  private static class RepaintScheduler {
    private final Alarm myAlarm = new Alarm();
    private final Set<RepaintRequest> myQueue = new LinkedHashSet<>();

    private void pushDirtyComponent(@NotNull Component c, final Rectangle rec) {
      ApplicationManager.getApplication().assertIsDispatchThread(); // assert myQueue accessed from EDT only
      myAlarm.cancelAllRequests();
      myAlarm.addRequest(() -> {
        for (RepaintRequest each : myQueue) {
          Rectangle r = each.getRectangle();
          if (r == null) {
            each.getComponent().repaint();
          }
          else {
            each.getComponent().repaint(r.x, r.y, r.width, r.height);
          }
        }
        myQueue.clear();
      }, 50);

      myQueue.add(new RepaintRequest(c, rec));
    }
  }

  private static class RepaintRequest {
    private final Component myComponent;
    private final Rectangle myRectangle;

    private RepaintRequest(@NotNull Component component, Rectangle rectangle) {
      myComponent = component;
      myRectangle = rectangle;
    }

    @NotNull
    public Component getComponent() {
      return myComponent;
    }

    public Rectangle getRectangle() {
      return myRectangle;
    }
  }

  interface IconListener<T> {
    void evalDone(DeferredIconImpl<T> source, T key, @NotNull Icon result);
  }

  static boolean equalIcons(Icon icon1, Icon icon2) {
    if (icon1 instanceof DeferredIconImpl) {
      return ((DeferredIconImpl)icon1).isDeferredAndEqual(icon2);
    }
    if (icon2 instanceof DeferredIconImpl) {
      return ((DeferredIconImpl)icon2).isDeferredAndEqual(icon1);
    }
    return Comparing.equal(icon1, icon2);
  }

  private boolean isDeferredAndEqual(Icon icon) {
    return icon instanceof DeferredIconImpl &&
           Comparing.equal(myParam, ((DeferredIconImpl)icon).myParam) &&
           equalIcons(myScaledDelegateIcon, ((DeferredIconImpl)icon).myScaledDelegateIcon);
  }

  @Override
  public String toString() {
    return "Deferred. Base=" + myScaledDelegateIcon;
  }
}
