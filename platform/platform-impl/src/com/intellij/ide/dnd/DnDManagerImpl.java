/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.dnd;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.reference.SoftReference;
import com.intellij.ui.MouseDragHelper;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.util.ui.GeometryUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.ref.WeakReference;

public class DnDManagerImpl extends DnDManager implements Disposable {
  private static final Logger LOG = Logger.getInstance("com.intellij.ide.dnd.DnDManager");

  @NonNls private static final String SOURCE_KEY = "DnD Source";
  @NonNls private static final String TARGET_KEY = "DnD Target";

  public static final Key<Pair<Image, Point>> DRAGGED_IMAGE_KEY = new Key<>("draggedImage");

  private DnDEventImpl myCurrentEvent;
  private DnDEvent myLastHighlightedEvent;

  private static final DnDTarget NULL_TARGET = new NullTarget();

  private WeakReference<DnDTarget> myLastProcessedTarget = new WeakReference<>(NULL_TARGET);
  private DragSourceContext myCurrentDragContext;

  private Component myLastProcessedOverComponent;
  private Point myLastProcessedPoint;
  private String myLastMessage;
  private DnDEvent myLastProcessedEvent;

  private final DragGestureListener myDragGestureListener = new MyDragGestureListener();
  private final DropTargetListener myDropTargetListener = new MyDropTargetListener();

  private static final Image EMPTY_IMAGE = UIUtil.createImage(1, 1, Transparency.TRANSLUCENT);

  private final Timer myTooltipTimer = UIUtil.createNamedTimer("DndManagerImpl tooltip timer",ToolTipManager.sharedInstance().getInitialDelay(), new ActionListener() {
    public void actionPerformed(ActionEvent e) {
      onTimer();
    }
  });
  private Runnable myHighlighterShowRequest;
  private Rectangle myLastHighlightedRec;
  private int myLastProcessedAction;

  private final Application myApp;

  private WeakReference<Component> myLastDropHandler;

  public DnDManagerImpl(final Application app) {
    myApp = app;
  }

  @Override
  public void dispose() {
  }

  public void registerSource(@NotNull final AdvancedDnDSource source) {
    if (!getApplication().isHeadlessEnvironment()) {
      final JComponent c = source.getComponent();
      registerSource(source, c);
    }
  }

  public void registerSource(DnDSource source, JComponent component) {
    if (!getApplication().isHeadlessEnvironment()) {
      component.putClientProperty(SOURCE_KEY, source);
      final DragSource defaultDragSource = DragSource.getDefaultDragSource();
      defaultDragSource.createDefaultDragGestureRecognizer(component, DnDConstants.ACTION_COPY_OR_MOVE, myDragGestureListener);
    }
  }

  public void unregisterSource(AdvancedDnDSource source) {
    final JComponent c = source.getComponent();
    unregisterSource(source, c);
  }

  public void unregisterSource(DnDSource source, JComponent component) {
    component.putClientProperty(SOURCE_KEY, null);

    cleanup(source, null, null);
  }

  private void cleanup(@Nullable final DnDSource source, @Nullable final DnDTarget target, @Nullable final JComponent targetComponent) {
    Runnable cleanup = () -> {
      if (shouldCancelCurrentDnDOperation(source, target, targetComponent)) {
        myLastProcessedOverComponent = null;
        myCurrentDragContext = null;
        resetEvents("cleanup");
      }
    };

    if (myApp.isDispatchThread()) {
      cleanup.run();
    } else {
      SwingUtilities.invokeLater(cleanup);
    }
  }

  private boolean shouldCancelCurrentDnDOperation(DnDSource source, DnDTarget target, JComponent targetComponent) {
    final DnDEvent currentDnDEvent = myLastProcessedEvent;
    if (currentDnDEvent == null) return true;
    
    if (source != null && currentDnDEvent.equals(source)) {
      return true;
    }
    
    if (target != null && targetComponent != null) {
      Component eachParent = targetComponent;
      while (eachParent != null) {
        if (target.equals(getTarget(eachParent))) {
          return true;
        }

        eachParent = eachParent.getParent();
      }
    }
    
    return false;
    
  }
  
  public void registerTarget(DnDTarget target, JComponent component) {
    if (!getApplication().isHeadlessEnvironment()) {
      component.putClientProperty(TARGET_KEY, target);
      new DropTarget(component, DnDConstants.ACTION_COPY_OR_MOVE, myDropTargetListener);
    }
  }

  public void unregisterTarget(DnDTarget target, JComponent component) {
    component.putClientProperty(TARGET_KEY, null);

    cleanup(null, target, component);
  }

  private DnDEventImpl updateCurrentEvent(Component aComponentOverDragging, Point aPoint, int nativeAction, @Nullable DataFlavor[] flavors, @Nullable Transferable transferable) {
    LOG.debug("updateCurrentEvent: " + aComponentOverDragging);

    DnDEventImpl currentEvent = myCurrentEvent;

    if (myCurrentEvent == null) {
      if (aComponentOverDragging instanceof JComponent) {
        JComponent jComp = (JComponent)aComponentOverDragging;
        DnDTarget target = getTarget(jComp);
        if (target instanceof DnDNativeTarget) {
          DnDEventImpl event = (DnDEventImpl)jComp.getClientProperty(DnDNativeTarget.EVENT_KEY);
          if (event == null) {
            DnDNativeTarget.EventInfo info = new DnDNativeTarget.EventInfo(flavors, transferable);
            event = new DnDEventImpl(this, DnDAction.COPY, info, aPoint);
            jComp.putClientProperty(DnDNativeTarget.EVENT_KEY, event);
          }

          currentEvent = event;
        }
      }
    }

    if (currentEvent == null) return currentEvent;

    final DnDAction dndAction = getDnDActionForPlatformAction(nativeAction);
    if (dndAction == null) return null;

    currentEvent.updateAction(dndAction);
    currentEvent.setPoint(aPoint);
    currentEvent.setHandlerComponent(aComponentOverDragging);

    boolean samePoint = currentEvent.getPoint().equals(myLastProcessedPoint);
    boolean sameComponent = currentEvent.getCurrentOverComponent().equals(myLastProcessedOverComponent);
    boolean sameAction = nativeAction == myLastProcessedAction;

    LOG.debug("updateCurrentEvent: point:" + aPoint);
    LOG.debug("updateCurrentEvent: action:" + nativeAction);

    if (samePoint && sameComponent && sameAction) {
      return currentEvent;
    }

    DnDTarget target = getTarget(aComponentOverDragging);
    DnDTarget immediateTarget = target;
    Component eachParent = aComponentOverDragging;

    final Pair<Image, Point> pair = currentEvent.getUserData(DRAGGED_IMAGE_KEY);
    if (pair != null) {
      target.updateDraggedImage(pair.first, aPoint, pair.second);
    }

    LOG.debug("updateCurrentEvent: action:" + nativeAction);

    while (true) {
      boolean canGoToParent = update(target, currentEvent);

      if (currentEvent.isDropPossible()) {
        if (currentEvent.wasDelegated()) {
          target = currentEvent.getDelegatedTarget();
        }
        break;
      }

      if (!canGoToParent) {
        break;
      }

      eachParent = findAllowedParentComponent(eachParent);
      if (eachParent == null) {
        break;
      }

      target = getTarget(eachParent);
    }

    LOG.debug("updateCurrentEvent: target:" + target);
    LOG.debug("updateCurrentEvent: immediateTarget:" + immediateTarget);

    if (!currentEvent.isDropPossible() && !immediateTarget.equals(target)) {
      update(immediateTarget, currentEvent);
    }

    updateCursor();

    final Container current = (Container)currentEvent.getCurrentOverComponent();
    final Point point = currentEvent.getPointOn(getLayeredPane(current));
    Rectangle inPlaceRect = new Rectangle(point.x - 5, point.y - 5, 5, 5);

    if (!currentEvent.equals(myLastProcessedEvent)) {
      hideCurrentHighlighter();
    }

    final DnDTarget processedTarget = getLastProcessedTarget();
    boolean sameTarget = processedTarget != null && processedTarget.equals(target);
    if (sameTarget) {
      if (currentEvent.isDropPossible()) {
        if (!myLastProcessedPoint.equals(currentEvent.getPoint())) {
          if (!Highlighters.isVisibleExcept(DnDEvent.DropTargetHighlightingType.TEXT | DnDEvent.DropTargetHighlightingType.ERROR_TEXT)) {
            hideCurrentHighlighter();
            queueTooltip(currentEvent, getLayeredPane(current), inPlaceRect);
          }
        }
      }
      else {
        if (myLastProcessedPoint == null || currentEvent == null || !myLastProcessedPoint.equals(currentEvent.getPoint())) {
          hideCurrentHighlighter();
          queueTooltip(currentEvent, getLayeredPane(current), inPlaceRect);
        }
      }
    }
    else {
      hideCurrentHighlighter();
      if (processedTarget != null) {
        processedTarget.cleanUpOnLeave();
      }
      currentEvent.clearDropHandler();

      if (!currentEvent.isDropPossible()) {
        queueTooltip(currentEvent, getLayeredPane(current), inPlaceRect);
      }
    }

    myLastProcessedTarget = new WeakReference<>(target);
    myLastProcessedPoint = currentEvent.getPoint();
    myLastProcessedOverComponent = currentEvent.getCurrentOverComponent();
    myLastProcessedAction = currentEvent.getAction().getActionId();
    myLastProcessedEvent = (DnDEvent)currentEvent.clone();

    return currentEvent;
  }

  private void updateCursor() {
    if (myCurrentDragContext == null || myCurrentEvent == null) return;

    Cursor cursor;
    if (myCurrentEvent.isDropPossible()) {
      cursor = myCurrentEvent.getCursor();
      if (cursor == null) {
        cursor = myCurrentEvent.getAction().getCursor();
      }

    }
    else {
      cursor = myCurrentEvent.getAction().getRejectCursor();
    }

    myCurrentDragContext.setCursor(cursor);
  }

  private boolean update(DnDTarget target, DnDEvent currentEvent) {
    LOG.debug("update target:" + target);

    currentEvent.clearDelegatedTarget();
    final boolean canGoToParent = target.update(currentEvent);


    String message;
    if (isMessageProvided(currentEvent)) {
      message = currentEvent.getExpectedDropResult();
    }
    else {
      message = "";
    }

    //final WindowManager wm = WindowManager.getInstance();
    //final StatusBar statusBar = wm.getStatusBar(target.getProject());
    //statusBar.setInfo(message);

    if (myLastMessage != null && !myLastMessage.equals(message)) {
      hideCurrentHighlighter();
    }
    myLastMessage = message;

    return canGoToParent;
  }

  private static Component findAllowedParentComponent(Component aComponentOverDragging) {
    Component eachParent = aComponentOverDragging;
    while (true) {
      eachParent = eachParent.getParent();
      if (eachParent == null) {
        return null;
      }

      final DnDTarget target = getTarget(eachParent);
      if (target != NULL_TARGET) {
        return eachParent;
      }
    }
  }

  private static DnDSource getSource(Component component) {
    if (component instanceof JComponent) {
      return (DnDSource)((JComponent)component).getClientProperty(SOURCE_KEY);
    }
    return null;
  }

  private static DnDTarget getTarget(Component component) {
    if (component instanceof JComponent) {
      DnDTarget target = (DnDTarget)((JComponent)component).getClientProperty(TARGET_KEY);
      if (target != null) return target;
    }

    return NULL_TARGET;
  }

  void showHighlighter(final Component aComponent, final int aType, final DnDEvent aEvent) {
    final Rectangle bounds = aComponent.getBounds();
    final Container parent = aComponent.getParent();

    showHighlighter(parent, aEvent, bounds, aType);
  }

  void showHighlighter(final RelativeRectangle rectangle, final int aType, final DnDEvent aEvent) {
    final JLayeredPane layeredPane = getLayeredPane(rectangle.getPoint().getComponent());
    final Rectangle bounds = rectangle.getRectangleOn(layeredPane);

    showHighlighter(layeredPane, aEvent, bounds, aType);
  }

  void showHighlighter(JLayeredPane layeredPane, final RelativeRectangle rectangle, final int aType, final DnDEvent event) {
    final Rectangle bounds = rectangle.getRectangleOn(layeredPane);
    showHighlighter(layeredPane, event, bounds, aType);
  }

  private boolean isEventBeingHighlighted(DnDEvent event) {
    return event.equals(getLastHighlightedEvent());
  }

  private void showHighlighter(final Component parent, final DnDEvent aEvent, final Rectangle bounds, final int aType) {
    final JLayeredPane layeredPane = getLayeredPane(parent);
    if (layeredPane == null) {
      return;
    }

    if (isEventBeingHighlighted(aEvent)) {
      if (GeometryUtil.isWithin(myLastHighlightedRec, aEvent.getPointOn(layeredPane))) {
        return;
      }
    }

    final Rectangle rectangle = SwingUtilities.convertRectangle(parent, bounds, layeredPane);
    setLastHighlightedEvent((DnDEvent)((DnDEventImpl)aEvent).clone(), rectangle);

    Highlighters.hide();

    Highlighters.show(aType, layeredPane, rectangle, aEvent);

    if (isMessageProvided(aEvent)) {
      queueTooltip(aEvent, layeredPane, rectangle);
    }
    else {
      Highlighters.hide(DnDEvent.DropTargetHighlightingType.TEXT | DnDEvent.DropTargetHighlightingType.ERROR_TEXT);
    }
  }

  private void queueTooltip(final DnDEvent aEvent, final JLayeredPane aLayeredPane, final Rectangle aRectangle) {
    myHighlighterShowRequest = () -> {
      if (myCurrentEvent != aEvent) return;
      Highlighters.hide(DnDEvent.DropTargetHighlightingType.TEXT | DnDEvent.DropTargetHighlightingType.ERROR_TEXT);
      if (aEvent.isDropPossible()) {
        Highlighters.show(DnDEvent.DropTargetHighlightingType.TEXT, aLayeredPane, aRectangle, aEvent);
      }
      else {
        Highlighters.show(DnDEvent.DropTargetHighlightingType.ERROR_TEXT, aLayeredPane, aRectangle, aEvent);
      }
    };
    myTooltipTimer.restart();
  }

  private static boolean isMessageProvided(final DnDEvent aEvent) {
    return aEvent.getExpectedDropResult() != null && aEvent.getExpectedDropResult().trim().length() > 0;
  }

  void hideCurrentHighlighter() {
    Highlighters.hide();
    clearRequest();
    setLastHighlightedEvent(null, null);
  }

  private void clearRequest() {
    myHighlighterShowRequest = null;
    myTooltipTimer.stop();
  }

  private void onTimer() {
    if (myHighlighterShowRequest != null) {
      myHighlighterShowRequest.run();
    }
    clearRequest();
  }
  

  private static JLayeredPane getLayeredPane(Component aComponent) {
    if (aComponent == null) return null;

    if (aComponent instanceof JLayeredPane) {
      return (JLayeredPane)aComponent;
    }

    if (aComponent instanceof JFrame) {
      return ((JFrame)aComponent).getRootPane().getLayeredPane();
    }

    if (aComponent instanceof JDialog) {
      return ((JDialog)aComponent).getRootPane().getLayeredPane();
    }

    final Window window = SwingUtilities.getWindowAncestor(aComponent);

    if (window instanceof JFrame) {
      return ((JFrame) window).getRootPane().getLayeredPane();
    }
    else if (window instanceof JDialog) {
      return ((JDialog) window).getRootPane().getLayeredPane();
    }

    return null;
  }

  private DnDTarget getLastProcessedTarget() {
    return myLastProcessedTarget.get();
  }

  private static class NullTarget implements DnDTarget {
    public boolean update(DnDEvent aEvent) {
      aEvent.setDropPossible(false, "You cannot drop anything here");
      return false;
    }

    public void drop(DnDEvent aEvent) {
    }

    public void cleanUpOnLeave() {
    }

    public void updateDraggedImage(Image image, Point dropPoint, Point imageOffset) {
    }
  }

  DnDEvent getCurrentEvent() {
    return myCurrentEvent;
  }

  private DnDEvent getLastHighlightedEvent() {
    return myLastHighlightedEvent;
  }

  private void setLastHighlightedEvent(@Nullable DnDEvent lastHighlightedEvent, @Nullable Rectangle aRectangle) {
    myLastHighlightedEvent = lastHighlightedEvent;
    myLastHighlightedRec = aRectangle;
  }

  private void resetEvents(@NonNls String s) {
    myCurrentEvent = resetEvent(myCurrentEvent);
    myLastProcessedEvent = resetEvent(myLastProcessedEvent);
    myLastHighlightedEvent = resetEvent(myLastHighlightedEvent);
    LOG.debug("Reset events: " + s);
  }

  @Nullable
  private static DnDEventImpl resetEvent(DnDEvent event) {
    if (event == null) return null;
    event.cleanUp();
    return null;
  }

  private class MyDragGestureListener implements DragGestureListener {
    public void dragGestureRecognized(DragGestureEvent dge) {
      try {
        final DnDSource source = getSource(dge.getComponent());
        if (source == null || !MouseDragHelper.checkModifiers(dge.getTriggerEvent())) return;

        DnDAction action = getDnDActionForPlatformAction(dge.getDragAction());
        if (source.canStartDragging(action, dge.getDragOrigin())) {

          if (myCurrentEvent == null) {
            // Actually, under Linux it is possible to get 2 or more dragGestureRecognized calls for single drag
            // operation. To reproduce:
            // 1. Do D-n-D in Styles tree
            // 2. Make an attempt to do D-n-D in Services tree
            // 3. Do D-n-D in Styles tree again.

            LOG.debug("Starting dragging for " + action);
            hideCurrentHighlighter();
            final DnDDragStartBean dnDDragStartBean = source.startDragging(action, dge.getDragOrigin());
            myCurrentEvent = new DnDEventImpl(DnDManagerImpl.this, action, dnDDragStartBean.getAttachedObject(), dnDDragStartBean.getPoint());
            myCurrentEvent.setOrgPoint(dge.getDragOrigin());

            Pair<Image, Point> pair = dnDDragStartBean.isEmpty() ? null : source.createDraggedImage(action, dge.getDragOrigin());
            if (pair == null) {
              pair = Pair.create(EMPTY_IMAGE, new Point(0, 0));
            }

            if (!DragSource.isDragImageSupported()) {
              // not all of the platforms supports image dragging (mswin doesn't, for example).
              myCurrentEvent.putUserData(DRAGGED_IMAGE_KEY, pair);
            }

            // mac osx fix: it will draw a border with size of the dragged component if there is no image provided.
            dge.startDrag(DragSource.DefaultCopyDrop, pair.first, pair.second, myCurrentEvent, new MyDragSourceListener(source));

            // check if source is also a target
            //        DnDTarget target = getTarget(dge.getComponent());
            //        if( target != null ) {
            //          target.update(myCurrentEvent);
            //        }
          }
        }
      }
      catch (InvalidDnDOperationException e) {
        LOG.info(e);
      }
    }
  }

  private static DnDAction getDnDActionForPlatformAction(int platformAction) {
    DnDAction action = null;
    switch (platformAction) {
      case DnDConstants.ACTION_COPY:
        action = DnDAction.COPY;
        break;
      case DnDConstants.ACTION_MOVE:
        action = DnDAction.MOVE;
        break;
      case DnDConstants.ACTION_LINK:
        action = DnDAction.LINK;
        break;
      default:
        break;
    }

    return action;
  }

  private class MyDragSourceListener implements DragSourceListener {
    private final DnDSource mySource;

    public MyDragSourceListener(final DnDSource source) {
      mySource = source;
    }

    public void dragEnter(DragSourceDragEvent dsde) {
      LOG.debug("dragEnter:" + dsde.getDragSourceContext().getComponent());
      myCurrentDragContext = dsde.getDragSourceContext();
    }

    public void dragOver(DragSourceDragEvent dsde) {
      LOG.debug("dragOver:" + dsde.getDragSourceContext().getComponent());
      myCurrentDragContext = dsde.getDragSourceContext();
    }

    public void dropActionChanged(DragSourceDragEvent dsde) {
      mySource.dropActionChanged(dsde.getGestureModifiers());
    }

    public void dragDropEnd(DragSourceDropEvent dsde) {
      mySource.dragDropEnd();
      final DnDTarget target = getLastProcessedTarget();
      if (target != null) {
        target.cleanUpOnLeave();
      }
      resetEvents("dragDropEnd:" + dsde.getDragSourceContext().getComponent());
      Highlighters.hide(DnDEvent.DropTargetHighlightingType.TEXT | DnDEvent.DropTargetHighlightingType.ERROR_TEXT);
    }

    public void dragExit(DragSourceEvent dse) {
      LOG.debug("Stop dragging1");
      onDragExit();
    }
  }

  private class MyDropTargetListener extends DropTargetAdapter {
    public void drop(final DropTargetDropEvent dtde) {
      try {
        final Component component = dtde.getDropTargetContext().getComponent();

        DnDEventImpl event =
          updateCurrentEvent(component, dtde.getLocation(), dtde.getDropAction(), dtde.getCurrentDataFlavors(), dtde.getTransferable());

        if (event != null && event.isDropPossible()) {
          dtde.acceptDrop(dtde.getDropAction());

          // do not wrap this into WriteAction!
          doDrop(component, event);

          if (event.shouldRemoveHighlighting()) {
            hideCurrentHighlighter();
          }
          dtde.dropComplete(true);
        }
        else {
          dtde.rejectDrop();
        }
      }
      catch (Throwable e) {
        LOG.error(e);
        dtde.rejectDrop();
      }
      finally {
        resetEvents("Stop dragging2");
      }
    }

    private void doDrop(Component component, DnDEventImpl currentEvent) {
      if (currentEvent.canHandleDrop()) {
        currentEvent.handleDrop();
      }
      else {
        getTarget(component).drop(currentEvent);
      }

      cleanTargetComponent(component);
      setLastDropHandler(component);

      myCurrentDragContext = null;
    }

    public void dragOver(DropTargetDragEvent dtde) {
      final DnDEventImpl event = updateCurrentEvent(dtde.getDropTargetContext().getComponent(), dtde.getLocation(), dtde.getDropAction(),
                                                    dtde.getCurrentDataFlavors(), dtde.getTransferable());
      if (myCurrentEvent == null) {
        if (event != null && event.isDropPossible()) {
          dtde.acceptDrag(event.getAction().getActionId());
        }
        else {
          dtde.rejectDrag();
        }
      }
    }

    public void dragExit(DropTargetEvent dte) {
      onDragExit();

      cleanTargetComponent(dte.getDropTargetContext().getComponent());
    }

    private void cleanTargetComponent(final Component c) {
      DnDTarget target = getTarget(c);
      if (target instanceof DnDNativeTarget && c instanceof JComponent) {
        ((JComponent)c).putClientProperty(DnDNativeTarget.EVENT_KEY, null);
      }
    }

    public void dropActionChanged(DropTargetDragEvent dtde) {
      updateCurrentEvent(dtde.getDropTargetContext().getComponent(), dtde.getLocation(), dtde.getDropAction(), dtde.getCurrentDataFlavors(), dtde.getTransferable());
    }
  }

  private void onDragExit() {
    if (myCurrentDragContext != null) {
      myCurrentDragContext.setCursor(null);
    }

    final DnDTarget target = getLastProcessedTarget();
    if (target != null) {
      target.cleanUpOnLeave();
    }
    hideCurrentHighlighter();
  }

  private Application getApplication() {
    return myApp;
  }

  public void setLastDropHandler(@Nullable Component c) {
    if (c == null) {
      myLastDropHandler = null;
    } else {
      myLastDropHandler = new WeakReference<>(c);
    }
  }

  @Nullable
  public Component getLastDropHandler() {
    return SoftReference.dereference(myLastDropHandler);
  }
}
