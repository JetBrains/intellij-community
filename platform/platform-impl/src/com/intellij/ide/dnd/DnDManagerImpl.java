// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.dnd;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.reference.SoftReference;
import com.intellij.ui.MouseDragHelper;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.util.ui.GeometryUtil;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.MultiResolutionImageProvider;
import com.intellij.util.ui.TimerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.lang.ref.WeakReference;

public final class DnDManagerImpl extends DnDManager {
  private static final Logger LOG = Logger.getInstance(DnDManagerImpl.class);

  @NonNls private static final String SOURCE_KEY = "DnD Source";
  @NonNls private static final String TARGET_KEY = "DnD Target";

  private static final Key<Pair<Image, Point>> DRAGGED_IMAGE_KEY = new Key<>("draggedImage");

  private DnDEventImpl myCurrentEvent;
  private DnDEvent myLastHighlightedEvent;

  private static final DnDTarget NULL_TARGET = new NullTarget();

  private WeakReference<DnDTarget> myLastProcessedTarget = new WeakReference<>(NULL_TARGET);
  private DragSourceContext myCurrentDragContext;

  private @Nullable WeakReference<Component> myLastProcessedOverComponent;
  private Point myLastProcessedPoint;
  private String myLastMessage;
  private DnDEvent myLastProcessedEvent;

  private final DragGestureListener myDragGestureListener = new MyDragGestureListener();
  private final DropTargetListener myDropTargetListener = new MyDropTargetListener();

  private static final Image EMPTY_IMAGE = ImageUtil.createImage(1, 1, Transparency.TRANSLUCENT);

  private final Timer myTooltipTimer =
    TimerUtil.createNamedTimer("DndManagerImpl tooltip timer", ToolTipManager.sharedInstance().getInitialDelay(), e -> onTimer());
  private Runnable myHighlighterShowRequest;
  private Rectangle myLastHighlightedRec;
  private int myLastProcessedAction;

  private WeakReference<Component> myLastDropHandler;

  @Override
  public void registerSource(@NotNull AdvancedDnDSource source) {
    registerSource(source, source.getComponent());
  }

  @Override
  public void registerSource(@NotNull DnDSource source, @NotNull JComponent component) {
    component.putClientProperty(SOURCE_KEY, source);
    DragSource defaultDragSource = DragSource.getDefaultDragSource();
    defaultDragSource.createDefaultDragGestureRecognizer(component, DnDConstants.ACTION_COPY_OR_MOVE, myDragGestureListener);
  }

  @Override
  public void registerSource(@NotNull DnDSource source, @NotNull JComponent component, @NotNull Disposable parentDisposable) {
    registerSource(source, component);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        unregisterSource(source, component);
      }
    });
  }

  @Override
  public void unregisterSource(@NotNull AdvancedDnDSource source) {
    unregisterSource(source, source.getComponent());
  }

  @Override
  public void unregisterSource(@NotNull DnDSource source, @NotNull JComponent component) {
    component.putClientProperty(SOURCE_KEY, null);
    cleanup(null, null);
  }

  private void cleanup(@Nullable final DnDTarget target, @Nullable final JComponent targetComponent) {
    Runnable cleanup = () -> {
      if (shouldCancelCurrentDnDOperation(target, targetComponent)) {
        myLastProcessedOverComponent = null;
        myCurrentDragContext = null;
        resetEvents("cleanup");
      }
    };

    if (ApplicationManager.getApplication().isDispatchThread()) {
      cleanup.run();
    }
    else {
      SwingUtilities.invokeLater(cleanup);
    }
  }

  private boolean shouldCancelCurrentDnDOperation(DnDTarget target, JComponent targetComponent) {
    DnDEvent currentDnDEvent = myLastProcessedEvent;
    if (currentDnDEvent == null) {
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

  @Override
  public void registerTarget(DnDTarget target, JComponent component) {
    component.putClientProperty(TARGET_KEY, target);
    new DropTarget(component, DnDConstants.ACTION_COPY_OR_MOVE, myDropTargetListener);
  }

  @Override
  public void registerTarget(@NotNull DnDTarget target, @NotNull JComponent component, @NotNull Disposable parentDisposable) {
    registerTarget(target, component);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        unregisterTarget(target, component);
      }
    });
  }

  @Override
  public void unregisterTarget(DnDTarget target, JComponent component) {
    component.putClientProperty(TARGET_KEY, null);

    cleanup(target, component);
  }

  private DnDEventImpl updateCurrentEvent(Component aComponentOverDragging, Point aPoint, int nativeAction, DataFlavor @Nullable [] flavors, @Nullable Transferable transferable) {
    LOG.debug("updateCurrentEvent: " + aComponentOverDragging);

    DnDEventImpl currentEvent = myCurrentEvent;

    if (myCurrentEvent == null && aComponentOverDragging instanceof JComponent) {
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

    if (currentEvent == null) return currentEvent;

    final DnDAction dndAction = getDnDActionForPlatformAction(nativeAction);
    if (dndAction == null) return null;

    currentEvent.updateAction(dndAction);
    currentEvent.setPoint(aPoint);
    currentEvent.setHandlerComponent(aComponentOverDragging);

    boolean samePoint = currentEvent.getPoint().equals(myLastProcessedPoint);
    Component component = myLastProcessedOverComponent != null ? myLastProcessedOverComponent.get() : null;
    boolean sameComponent = currentEvent.getCurrentOverComponent().equals(component);
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

    Container current = (Container)currentEvent.getCurrentOverComponent();
    Point point = currentEvent.getPointOn(getLayeredPane(current));
    Rectangle inPlaceRect = new Rectangle(point.x - 5, point.y - 5, 5, 5);

    if (!currentEvent.equals(myLastProcessedEvent)) {
      hideCurrentHighlighter();
    }

    DnDTarget processedTarget = getLastProcessedTarget();
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
      else if (myLastProcessedPoint == null || !myLastProcessedPoint.equals(currentEvent.getPoint())) {
        hideCurrentHighlighter();
        queueTooltip(currentEvent, getLayeredPane(current), inPlaceRect);
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
    myLastProcessedOverComponent = new WeakReference<>(currentEvent.getCurrentOverComponent());
    myLastProcessedAction = currentEvent.getAction().getActionId();
    myLastProcessedEvent = (DnDEvent)currentEvent.clone();

    return currentEvent;
  }

  private void updateCursor() {
    if (myCurrentDragContext == null || myCurrentEvent == null) {
      return;
    }

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

  private boolean update(DnDTargetChecker target, DnDEvent currentEvent) {
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

  @Nullable
  private static Component findAllowedParentComponent(@NotNull Component aComponentOverDragging) {
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

  @Nullable
  private static DnDSource getSource(Component component) {
    if (component instanceof JComponent) {
      return (DnDSource)((JComponent)component).getClientProperty(SOURCE_KEY);
    }
    return null;
  }

  private static DnDTarget getTarget(Component component) {
    if (component instanceof JComponent) {
      DnDTarget target = (DnDTarget)((JComponent)component).getClientProperty(TARGET_KEY);
      if (target != null) {
        return target;
      }
    }
    return NULL_TARGET;
  }

  void showHighlighter(Component aComponent, int aType, DnDEvent aEvent) {
    showHighlighter(aComponent.getParent(), aEvent, aComponent.getBounds(), aType);
  }

  void showHighlighter(RelativeRectangle rectangle, int aType, DnDEvent aEvent) {
    JLayeredPane layeredPane = getLayeredPane(rectangle.getPoint().getComponent());
    Rectangle bounds = rectangle.getRectangleOn(layeredPane);
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

  @Nullable
  private static JLayeredPane getLayeredPane(@Nullable Component aComponent) {
    if (aComponent == null) {
      return null;
    }
    else if (aComponent instanceof JLayeredPane) {
      return (JLayeredPane)aComponent;
    }
    else if (aComponent instanceof JFrame) {
      return ((JFrame)aComponent).getRootPane().getLayeredPane();
    }
    else if (aComponent instanceof JDialog) {
      return ((JDialog)aComponent).getRootPane().getLayeredPane();
    }

    Window window = SwingUtilities.getWindowAncestor(aComponent);
    if (window instanceof JFrame) {
      return ((JFrame)window).getRootPane().getLayeredPane();
    }
    else if (window instanceof JDialog) {
      return ((JDialog)window).getRootPane().getLayeredPane();
    }
    return null;
  }

  private DnDTarget getLastProcessedTarget() {
    return myLastProcessedTarget.get();
  }

  private static class NullTarget implements DnDTarget {
    @Override
    public boolean update(DnDEvent aEvent) {
      aEvent.setDropPossible(false, "You cannot drop anything here");
      return false;
    }

    @Override
    public void drop(DnDEvent aEvent) {
    }
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

  private final class MyDragGestureListener implements DragGestureListener {
    @Override
    public void dragGestureRecognized(DragGestureEvent event) {
      try {
        DnDSource source = getSource(event.getComponent());
        // Actually, under Linux it is possible to get 2 or more dragGestureRecognized calls for single drag
        // operation. To reproduce:
        // 1. Do D-n-D in Styles tree
        // 2. Make an attempt to do D-n-D in Services tree
        // 3. Do D-n-D in Styles tree again.

        if (source == null || !MouseDragHelper.checkModifiers(event.getTriggerEvent()) || myCurrentEvent != null) {
          return;
        }

        DnDAction action = getDnDActionForPlatformAction(event.getDragAction());
        if (!source.canStartDragging(action, event.getDragOrigin())) {
          return;
        }

        LOG.debug("Starting dragging for " + action);
        hideCurrentHighlighter();
        DnDDragStartBean bean = source.startDragging(action, event.getDragOrigin());
        myCurrentEvent = new DnDEventImpl(DnDManagerImpl.this, action, bean.getAttachedObject(), bean.getPoint());
        myCurrentEvent.setOrgPoint(event.getDragOrigin());

        Pair<Image, Point> pair = bean.isEmpty() ? null : source.createDraggedImage(action, event.getDragOrigin(), bean);
        if (pair == null) {
          pair = Pair.create(EMPTY_IMAGE, new Point(0, 0));
        }

        if (SystemInfo.isMac && MultiResolutionImageProvider.isMultiResolutionImageAvailable()) {
          Image mrImage = MultiResolutionImageProvider.convertFromJBImage(pair.first);
          if (mrImage != null) pair = new Pair<>(mrImage, pair.second);
        }

        if (!DragSource.isDragImageSupported()) {
          // not all of the platforms supports image dragging (mswin doesn't, for example).
          myCurrentEvent.putUserData(DRAGGED_IMAGE_KEY, pair);
        }

        // mac osx fix: it will draw a border with size of the dragged component if there is no image provided.
        event.startDrag(DragSource.DefaultCopyDrop, pair.first, pair.second, myCurrentEvent, new MyDragSourceListener(source));
      }
      catch (InvalidDnDOperationException e) {
        LOG.info(e);
      }
    }
  }

  private static DnDAction getDnDActionForPlatformAction(int platformAction) {
    DnDAction action = null;
    boolean altOnly = UISettings.getInstance().getDndWithPressedAltOnly();
    switch (platformAction) {
      case DnDConstants.ACTION_COPY:
        action = altOnly ? DnDAction.MOVE : DnDAction.COPY;
        break;
      case DnDConstants.ACTION_MOVE:
        action = altOnly? DnDAction.COPY : DnDAction.MOVE;
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

    MyDragSourceListener(final DnDSource source) {
      mySource = source;
    }

    @Override
    public void dragEnter(DragSourceDragEvent dsde) {
      LOG.debug("dragEnter:" + dsde.getDragSourceContext().getComponent());
      myCurrentDragContext = dsde.getDragSourceContext();
    }

    @Override
    public void dragOver(DragSourceDragEvent dsde) {
      LOG.debug("dragOver:" + dsde.getDragSourceContext().getComponent());
      myCurrentDragContext = dsde.getDragSourceContext();
    }

    @Override
    public void dropActionChanged(DragSourceDragEvent dsde) {
      mySource.dropActionChanged(dsde.getGestureModifiers());
    }

    @Override
    public void dragDropEnd(DragSourceDropEvent dsde) {
      mySource.dragDropEnd();
      final DnDTarget target = getLastProcessedTarget();
      if (target != null) {
        target.cleanUpOnLeave();
      }
      resetEvents("dragDropEnd:" + dsde.getDragSourceContext().getComponent());
      Highlighters.hide(DnDEvent.DropTargetHighlightingType.TEXT | DnDEvent.DropTargetHighlightingType.ERROR_TEXT);
      myCurrentDragContext = null;
    }

    @Override
    public void dragExit(DragSourceEvent dse) {
      LOG.debug("Stop dragging1");
      onDragExit();
    }
  }

  private class MyDropTargetListener implements DropTargetListener {
    @Override
    public void drop(final DropTargetDropEvent dtde) {
      SmoothAutoScroller.getSharedListener().drop(dtde);
      try {
        final Component component = dtde.getDropTargetContext().getComponent();

        DnDEventImpl event =
          updateCurrentEvent(component, dtde.getLocation(), dtde.getDropAction(), dtde.getCurrentDataFlavors(), dtde.getTransferable());

        if (event != null && event.isDropPossible()) {
          dtde.acceptDrop(dtde.getDropAction());

          // do not wrap this into WriteAction!
          boolean success = doDrop(component, event);

          if (event.shouldRemoveHighlighting()) {
            hideCurrentHighlighter();
          }
          dtde.dropComplete(success);
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

    private boolean doDrop(Component component, DnDEventImpl currentEvent) {
      boolean success = true;
      if (currentEvent.canHandleDrop()) {
        currentEvent.handleDrop();
      }
      else {
        DnDTarget target = getTarget(component);
        if (target instanceof DnDDropHandler.WithResult) {
          success = ((DnDDropHandler.WithResult)target).tryDrop(currentEvent);
        }
        else {
          target.drop(currentEvent);
        }
      }

      cleanTargetComponent(component);
      setLastDropHandler(component);

      myCurrentDragContext = null;
      return success;
    }

    @Override
    public void dragEnter(DropTargetDragEvent dtde) {
      SmoothAutoScroller.getSharedListener().dragEnter(dtde);
    }

    @Override
    public void dragOver(DropTargetDragEvent dtde) {
      SmoothAutoScroller.getSharedListener().dragOver(dtde);
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

    @Override
    public void dragExit(DropTargetEvent dte) {
      SmoothAutoScroller.getSharedListener().dragExit(dte);
      onDragExit();

      cleanTargetComponent(dte.getDropTargetContext().getComponent());
    }

    private void cleanTargetComponent(final Component c) {
      DnDTarget target = getTarget(c);
      if (target instanceof DnDNativeTarget && c instanceof JComponent) {
        ((JComponent)c).putClientProperty(DnDNativeTarget.EVENT_KEY, null);
      }
    }

    @Override
    public void dropActionChanged(DropTargetDragEvent dtde) {
      SmoothAutoScroller.getSharedListener().dropActionChanged(dtde);
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

  public void setLastDropHandler(@Nullable Component c) {
    if (c == null) {
      myLastDropHandler = null;
    } else {
      myLastDropHandler = new WeakReference<>(c);
    }
  }

  @Override
  @Nullable
  public Component getLastDropHandler() {
    return SoftReference.dereference(myLastDropHandler);
  }
}
