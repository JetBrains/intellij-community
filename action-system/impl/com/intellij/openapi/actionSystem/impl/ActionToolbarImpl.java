package com.intellij.openapi.actionSystem.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManagerListener;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.keymap.ex.WeakKeymapManagerListener;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

public class ActionToolbarImpl extends JPanel implements ActionToolbar {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.actionSystem.impl.ActionToolbarImpl");

  /**
   * This array contains Rectangles which define bounds of the corresponding
   * components in the toolbar. This list can be considerer as a cache of the
   * Rectangle objects that are used in calculation of preferred sizes and
   * layouting of components.
   */
  private final ArrayList<Rectangle> myComponentBounds = new ArrayList<Rectangle>();

  /**
   * protected for fabrique
   */
  protected Dimension myMinimumButtonSize;
  /**
   * @see ActionToolbar#getLayoutPolicy()
   */
  private int myLayoutPolicy;
  private int myOrientation;
  private final ActionGroup myActionGroup;
  private final String myPlace;
  private final MyKeymapManagerListener myKeymapManagerListener;
  private final MyTimerListener myTimerListener;
  private ArrayList<AnAction> myNewVisibleActions;
  protected ArrayList<AnAction> myVisibleActions;
  /**
   * protected for fabrique
   */
  protected final PresentationFactory myPresentationFactory;
  /**
   * @see ActionToolbar#adjustTheSameSize(boolean)
   */
  private boolean myAdjustTheSameSize;

  private ActionButtonLook myButtonLook = null;
  private DataManager myDataManager;
  protected ActionManagerEx myActionManager;

  private Rectangle myAutoPopupRec;
  private Icon myAutoPopupIcon = IconLoader.getIcon("/ide/link.png");
  private KeymapManagerEx myKeymapManager;
  private PopupToolbar myPopupToolbar;
  private int myFirstOusideIndex = -1;

  private JBPopup myPopup;
  private JComponent myTargetComponent;

  public ActionToolbarImpl(final String place,
                           final ActionGroup actionGroup,
                           final boolean horizontal,
                           DataManager dataManager,
                           ActionManagerEx actionManager,
                           KeymapManagerEx keymapManager) {
    super(null);
    myActionManager = actionManager;
    myKeymapManager = keymapManager;
    setMinimumButtonSize(DEFAULT_MINIMUM_BUTTON_SIZE);
    setLayoutPolicy(AUTO_LAYOUT_POLICY);
    setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
    myPlace = place;
    myActionGroup = actionGroup;
    myPresentationFactory = new PresentationFactory();
    myKeymapManagerListener = new MyKeymapManagerListener();
    myTimerListener = new MyTimerListener();
    myVisibleActions = new ArrayList<AnAction>();
    myNewVisibleActions = new ArrayList<AnAction>();
    myDataManager = dataManager;

    setLayout(new BorderLayout());
    setOrientation(horizontal ? SwingConstants.HORIZONTAL : SwingConstants.VERTICAL);
    updateActionsImmediately();
    //
    keymapManager.addKeymapManagerListener(new WeakKeymapManagerListener(keymapManager, myKeymapManagerListener));
    actionManager.addTimerListener(500, new WeakTimerListener(actionManager, myTimerListener));
    // If the panel doesn't handle mouse event then it will be passed to its parent.
    // It means that if the panel is in slidindg mode then the focus goes to the editor
    // and panel will be automatically hidden.
    enableEvents(MouseEvent.MOUSE_MOTION_EVENT_MASK | MouseEvent.MOUSE_EVENT_MASK);
  }

  public JComponent getComponent() {
    return this;
  }

  public int getLayoutPolicy() {
    return myLayoutPolicy;
  }

  public void setLayoutPolicy(final int layoutPolicy) {
    if (layoutPolicy != NOWRAP_LAYOUT_POLICY && layoutPolicy != WRAP_LAYOUT_POLICY && layoutPolicy != AUTO_LAYOUT_POLICY) {
      throw new IllegalArgumentException("wrong layoutPolicy: " + layoutPolicy);
    }
    myLayoutPolicy = layoutPolicy;
  }

  protected void paintComponent(final Graphics g) {
    super.paintComponent(g);

    if (myLayoutPolicy == AUTO_LAYOUT_POLICY) {
      if (myAutoPopupRec != null) {
        if (myOrientation == SwingUtilities.HORIZONTAL) {
          final int dy = myAutoPopupRec.height / 2 - myAutoPopupIcon.getIconHeight() / 2;
          myAutoPopupIcon.paintIcon(this, g, (int)myAutoPopupRec.getMaxX() - myAutoPopupIcon.getIconWidth() - 1, myAutoPopupRec.y + dy);
        }
        else {
          final int dx = myAutoPopupRec.width / 2 - myAutoPopupIcon.getIconWidth() / 2;
          myAutoPopupIcon.paintIcon(this, g, myAutoPopupRec.x + dx, (int)myAutoPopupRec.getMaxY() - myAutoPopupIcon.getIconWidth() - 1);
        }
      }
    }
  }

  private void fillToolBar(final ArrayList<AnAction> actions) {
    for (int i = 0; i < actions.size(); i++) {
      final AnAction action = actions.get(i);
      if (action instanceof Separator) {
        if (i > 0 && i < actions.size() - 1) {
          add(new MySeparator());
        }
      }
      else if (action instanceof CustomComponentAction) {
        add(((CustomComponentAction)action).createCustomComponent(myPresentationFactory.getPresentation(action)));
      }
      else {
        final ActionButton button = createToolbarButton(action);
        add(button);
      }
    }
  }

  protected ActionButton createToolbarButton(final AnAction action) {
    if (action.displayTextInToolbar()) {
      return new ActionButtonWithText(action, myPresentationFactory.getPresentation(action), myPlace,
                                      ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
    }

    final ActionButton actionButton = new ActionButton(action, myPresentationFactory.getPresentation(action), myPlace, myMinimumButtonSize);
    actionButton.setLook(myButtonLook);
    return actionButton;
  }

  public void doLayout() {
    if (!isValid()) {
      calculateBounds(getSize(), myComponentBounds);
    }
    final int componentCount = getComponentCount();
    LOG.assertTrue(componentCount <= myComponentBounds.size());
    for (int i = componentCount - 1; i >= 0; i--) {
      final Component component = getComponent(i);
      component.setBounds(myComponentBounds.get(i));
    }
  }

  public void validate() {
    if (!isValid()) {
      calculateBounds(getSize(), myComponentBounds);
      super.validate();
    }
  }

  /**
   * @return maximum button width
   */
  private int getMaxButtonWidth() {
    int width = 0;
    for (int i = 0; i < getComponentCount(); i++) {
      final Dimension dimension = getComponent(i).getPreferredSize();
      width = Math.max(width, dimension.width);
    }
    return width;
  }

  /**
   * @return maximum button height
   */
  public int getMaxButtonHeight() {
    int height = 0;
    for (int i = 0; i < getComponentCount(); i++) {
      final Dimension dimension = getComponent(i).getPreferredSize();
      height = Math.max(height, dimension.height);
    }
    return height;
  }

  private void calculateBoundsNowrapImpl(ArrayList<Rectangle> bounds) {
    final int componentCount = getComponentCount();
    LOG.assertTrue(componentCount <= bounds.size());

    final int width = getWidth();
    final int height = getHeight();

    if (myAdjustTheSameSize) {
      final int maxWidth = getMaxButtonWidth();
      final int maxHeight = getMaxButtonHeight();

      if (myOrientation == SwingConstants.HORIZONTAL) {
        int xOffset = 0;
        for (int i = 0; i < componentCount; i++) {
          final Rectangle r = bounds.get(i);
          r.setBounds(xOffset, (height - maxHeight) / 2, maxWidth, maxHeight);
          xOffset += maxWidth;
        }
      }
      else {
        int yOffset = 0;
        for (int i = 0; i < componentCount; i++) {
          final Rectangle r = bounds.get(i);
          r.setBounds((width - maxWidth) / 2, yOffset, maxWidth, maxHeight);
          yOffset += maxHeight;
        }
      }
    }
    else {
      if (myOrientation == SwingConstants.HORIZONTAL) {
        final int maxHeight = getMaxButtonHeight();
        int xOffset = 0;
        final int yOffset = 0;
        for (int i = 0; i < componentCount; i++) {
          final Component component = getComponent(i);
          final Dimension d = component.getPreferredSize();
          final Rectangle r = bounds.get(i);
          r.setBounds(xOffset, yOffset + (maxHeight - d.height) / 2, d.width, d.height);
          xOffset += d.width;
        }
      }
      else {
        final int maxWidth = getMaxButtonWidth();
        final int xOffset = 0;
        int yOffset = 0;
        for (int i = 0; i < componentCount; i++) {
          final Component component = getComponent(i);
          final Dimension d = component.getPreferredSize();
          final Rectangle r = bounds.get(i);
          r.setBounds(xOffset + (maxWidth - d.width) / 2, yOffset, d.width, d.height);
          yOffset += d.height;
        }
      }
    }
  }

  private void calculateBoundsAutoImp(Dimension sizeToFit, ArrayList<Rectangle> bounds) {
    final int componentCount = getComponentCount();
    LOG.assertTrue(componentCount <= bounds.size());

    final boolean actualLayout = bounds == myComponentBounds;

    if (actualLayout) {
      myAutoPopupRec = null;
    }

    int autoButtonSize = myAutoPopupIcon.getIconWidth();
    boolean full = false;

    if (myOrientation == SwingConstants.HORIZONTAL) {
      int eachX = 0;
      int eachY = 0;
      for (int i = 0; i < componentCount; i++) {
        final Rectangle eachBound = new Rectangle(getComponent(i).getPreferredSize());
        if (!full) {
          boolean outside;
          if (i < componentCount - 1) {
            outside = eachX + eachBound.width + autoButtonSize < sizeToFit.width;
          }
          else {
            outside = eachX + eachBound.width < sizeToFit.width;
          }

          if (outside) {
            eachBound.x = eachX;
            eachBound.y = eachY;
            eachX += eachBound.width;
          }
          else {
            full = true;
          }
        }

        if (full) {
          if (myAutoPopupRec == null) {
            myAutoPopupRec = new Rectangle(eachX, eachY, sizeToFit.width - eachX - 1, sizeToFit.height - 1);
            myFirstOusideIndex = i;
          }
          eachBound.x = Integer.MAX_VALUE;
          eachBound.y = Integer.MAX_VALUE;
        }

        bounds.get(i).setBounds(eachBound);
      }
    }
    else {
      int eachX = 0;
      int eachY = 0;
      for (int i = 0; i < componentCount; i++) {
        final Rectangle eachBound = new Rectangle(getComponent(i).getPreferredSize());
        if (!full) {
          boolean outside;
          if (i < componentCount - 1) {
            outside = eachY + eachBound.height + autoButtonSize < sizeToFit.height;
          }
          else {
            outside = eachY + eachBound.height < sizeToFit.height;
          }
          if (outside) {
            eachBound.x = eachX;
            eachBound.y = eachY;
            eachY += eachBound.height;
          }
          else {
            full = true;
          }
        }

        if (full) {
          if (myAutoPopupRec == null) {
            myAutoPopupRec = new Rectangle(eachX, eachY, sizeToFit.width - 1, sizeToFit.height - eachY - 1);
            myFirstOusideIndex = i;
          }
          eachBound.x = Integer.MAX_VALUE;
          eachBound.y = Integer.MAX_VALUE;
        }

        bounds.get(i).setBounds(eachBound);
      }
    }

  }

  private void calculateBoundsWrapImpl(Dimension sizeToFit, ArrayList<Rectangle> bounds) {
    // We have to gracefull handle case when toolbar was not layed out yet.
    // In this case we calculate bounds as it is a NOWRAP toolbar.
    if (getWidth() == 0 || getHeight() == 0) {
      try {
        setLayoutPolicy(NOWRAP_LAYOUT_POLICY);
        calculateBoundsNowrapImpl(bounds);
      }
      finally {
        setLayoutPolicy(WRAP_LAYOUT_POLICY);
      }
      return;
    }


    final int componentCount = getComponentCount();
    LOG.assertTrue(componentCount <= bounds.size());

    if (myAdjustTheSameSize) {
      if (myOrientation == SwingConstants.HORIZONTAL) {
        final int maxWidth = getMaxButtonWidth();
        final int maxHeight = getMaxButtonHeight();

        // Lay components out
        int xOffset = 0;
        int yOffset = 0;
        // Calculate max size of a row. It's not possible to make more then 3 row toolbar
        final int maxRowWidth = Math.max(sizeToFit.width, componentCount * maxWidth / 3);
        for (int i = 0; i < componentCount; i++) {
          if (xOffset + maxWidth > maxRowWidth) { // place component at new row
            xOffset = 0;
            yOffset += maxHeight;
          }

          final Rectangle each = bounds.get(i);
          each.setBounds(xOffset, maxWidth, yOffset, maxHeight);

          xOffset += maxWidth;
        }
      }
      else {
        final int maxWidth = getMaxButtonWidth();
        final int maxHeight = getMaxButtonHeight();

        // Lay components out
        int xOffset = 0;
        int yOffset = 0;
        // Calculate max size of a row. It's not possible to make more then 3 column toolbar
        final int maxRowHeight = Math.max(sizeToFit.height, componentCount * myMinimumButtonSize.height / 3);
        for (int i = 0; i < componentCount; i++) {
          if (yOffset + maxHeight > maxRowHeight) { // place component at new row
            yOffset = 0;
            xOffset += maxWidth;
          }

          final Rectangle each = bounds.get(i);
          each.setBounds(xOffset, maxWidth, yOffset, maxHeight);

          yOffset += maxHeight;
        }
      }
    }
    else {
      if (myOrientation == SwingConstants.HORIZONTAL) {
        // Calculate row height
        int rowHeight = 0;
        final Dimension[] dims = new Dimension[componentCount]; // we will use this dimesions later
        for (int i = 0; i < componentCount; i++) {
          dims[i] = getComponent(i).getPreferredSize();
          final int height = dims[i].height;
          rowHeight = Math.max(rowHeight, height);
        }

        // Lay components out
        int xOffset = 0;
        int yOffset = 0;
        // Calculate max size of a row. It's not possible to make more then 3 row toolbar
        final int maxRowWidth = Math.max(getWidth(), componentCount * myMinimumButtonSize.width / 3);
        for (int i = 0; i < componentCount; i++) {
          final Dimension d = dims[i];
          if (xOffset + d.width > maxRowWidth) { // place component at new row
            xOffset = 0;
            yOffset += rowHeight;
          }

          final Rectangle each = bounds.get(i);
          each.setBounds(xOffset, yOffset + (rowHeight - d.height) / 2, d.width, d.height);

          xOffset += d.width;
        }
      }
      else {
        // Calculate row width
        int rowWidth = 0;
        final Dimension[] dims = new Dimension[componentCount]; // we will use this dimesions later
        for (int i = 0; i < componentCount; i++) {
          dims[i] = getComponent(i).getPreferredSize();
          final int width = dims[i].width;
          rowWidth = Math.max(rowWidth, width);
        }

        // Lay components out
        int xOffset = 0;
        int yOffset = 0;
        // Calculate max size of a row. It's not possible to make more then 3 column toolbar
        final int maxRowHeight = Math.max(getHeight(), componentCount * myMinimumButtonSize.height / 3);
        for (int i = 0; i < componentCount; i++) {
          final Dimension d = dims[i];
          if (yOffset + d.height > maxRowHeight) { // place component at new row
            yOffset = 0;
            xOffset += rowWidth;
          }

          final Rectangle each = bounds.get(i);
          each.setBounds(xOffset + (rowWidth - d.width) / 2, yOffset, d.width, d.height);

          yOffset += d.height;
        }
      }
    }
  }

  /**
   * Calculates bounds of all the components in the toolbar
   */
  private void calculateBounds(Dimension size2Fit, ArrayList<Rectangle> bounds) {
    bounds.clear();
    for (int i = 0; i < getComponentCount(); i++) {
      bounds.add(new Rectangle());
    }

    if (myLayoutPolicy == NOWRAP_LAYOUT_POLICY) {
      calculateBoundsNowrapImpl(bounds);
    }
    else if (myLayoutPolicy == WRAP_LAYOUT_POLICY) {
      calculateBoundsWrapImpl(size2Fit, bounds);
    }
    else if (myLayoutPolicy == AUTO_LAYOUT_POLICY) {
      calculateBoundsAutoImp(size2Fit, bounds);
    }
    else {
      throw new IllegalStateException("unknonw layoutPolicy: " + myLayoutPolicy);
    }
  }

  public Dimension getPreferredSize() {
    final ArrayList<Rectangle> bounds = new ArrayList<Rectangle>();
    for (int i = 0; i < getComponentCount(); i++) {
      bounds.add(new Rectangle());
    }
    calculateBounds(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE), bounds);

    int xLeft = Integer.MAX_VALUE;
    int yTop = Integer.MAX_VALUE;
    int xRight = Integer.MIN_VALUE;
    int yBottom = Integer.MIN_VALUE;
    for (int i = bounds.size() - 1; i >= 0; i--) {
      final Rectangle each = bounds.get(i);
      if (each.x == Integer.MAX_VALUE) continue;
      xLeft = Math.min(xLeft, each.x);
      yTop = Math.min(yTop, each.y);
      xRight = Math.max(xRight, each.x + each.width);
      yBottom = Math.max(yBottom, each.y + each.height);
    }
    final Dimension dimension = new Dimension(xRight - xLeft, yBottom - yTop);

    if (myLayoutPolicy == AUTO_LAYOUT_POLICY) {
      if (myOrientation == SwingUtilities.HORIZONTAL) {
        dimension.width += myAutoPopupIcon.getIconWidth();
      }
      else {
        dimension.height += myAutoPopupIcon.getIconHeight();
      }
    }

    return dimension;
  }

  public Dimension getMinimumSize() {
    if (myLayoutPolicy == AUTO_LAYOUT_POLICY) {
      return new Dimension(myAutoPopupIcon.getIconWidth(), myMinimumButtonSize.height);
    }
    else {
      return super.getMinimumSize();
    }
  }

  private final class MySeparator extends JComponent {
    private final Dimension mySize;

    public MySeparator() {
      if (myOrientation == SwingConstants.HORIZONTAL) {
        mySize = new Dimension(6, 24);
      }
      else {
        mySize = new Dimension(24, 6);
      }
    }

    public Dimension getPreferredSize() {
      return mySize;
    }

    protected void paintComponent(final Graphics g) {
      g.setColor(UIUtil.getSeparatorShadow());
      if (getParent() != null) {
        if (myOrientation == SwingConstants.HORIZONTAL) {
          UIUtil.drawLine(g, 3, 2, 3, getParent().getSize().height - 2);
        }
        else {
          UIUtil.drawLine(g, 2, 3, getParent().getSize().width - 2, 3);
        }
      }
    }
  }

  private final class MyKeymapManagerListener implements KeymapManagerListener {
    public void activeKeymapChanged(final Keymap keymap) {
      final int componentCount = getComponentCount();
      for (int i = 0; i < componentCount; i++) {
        final Component component = getComponent(i);
        if (component instanceof ActionButton) {
          ((ActionButton)component).updateToolTipText();
        }
      }
    }
  }

  private final class MyTimerListener implements TimerListener {
    public ModalityState getModalityState() {
      return ModalityState.stateForComponent(ActionToolbarImpl.this);
    }

    public void run() {
      if (!ActionToolbarImpl.this.isShowing()) {
        return;
      }

      Window mywindow = SwingUtilities.windowForComponent(ActionToolbarImpl.this);
      if (mywindow != null && !mywindow.isActive() && !mywindow.isFocused()) return;

      // do not update when a popup menu is shown (if popup menu contains action which is also in the toolbar, it should not be enabled/disabled)
      final MenuSelectionManager menuSelectionManager = MenuSelectionManager.defaultManager();
      final MenuElement[] selectedPath = menuSelectionManager.getSelectedPath();
      if (selectedPath.length > 0) {
        return;
      }

      // don't update toolbar if there is currently active modal dialog

      final Window window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
      if (window instanceof Dialog) {
        final Dialog dialog = (Dialog)window;
        if (dialog.isModal() && !SwingUtilities.isDescendingFrom(ActionToolbarImpl.this, dialog)) {
          return;
        }
      }

      updateActionsImmediately();
    }
  }

  public void adjustTheSameSize(final boolean value) {
    if (myAdjustTheSameSize == value) {
      return;
    }
    myAdjustTheSameSize = value;
    revalidate();
  }

  public void setMinimumButtonSize(@NotNull final Dimension size) {
    myMinimumButtonSize = size;
    for (int i = getComponentCount() - 1; i >= 0; i--) {
      final Component component = getComponent(i);
      if (component instanceof ActionButton) {
        final ActionButton button = (ActionButton)component;
        button.setMinimumButtonSize(size);
      }
    }
    revalidate();
  }

  public void setOrientation(final int orientation) {
    if (SwingConstants.HORIZONTAL != orientation && SwingConstants.VERTICAL != orientation) {
      throw new IllegalArgumentException("wrong orientation: " + orientation);
    }
    myOrientation = orientation;
  }

  public void updateActionsImmediately() {
    myNewVisibleActions.clear();
    final DataContext dataContext = getDataContext();

    Utils.expandActionGroup(myActionGroup, myNewVisibleActions, myPresentationFactory, dataContext, myPlace, myActionManager);

    if (!myNewVisibleActions.equals(myVisibleActions)) {
      // should rebuild UI

      final boolean changeBarVisibility = myNewVisibleActions.size() == 0 || myVisibleActions.size() == 0;

      final ArrayList<AnAction> temp = myVisibleActions;
      myVisibleActions = myNewVisibleActions;
      myNewVisibleActions = temp;

      removeAll();
      fillToolBar(myVisibleActions);

      if (changeBarVisibility) {
        revalidate();
      }
      else {
        final Container parent = getParent();
        if (parent != null) {
          parent.invalidate();
          parent.validate();
        }
      }
      repaint();
    }
  }

  public void setTargetComponent(final JComponent component) {
    myTargetComponent = component;
  }

  protected DataContext getDataContext() {
    return myTargetComponent != null ? myDataManager.getDataContext(myTargetComponent) : ((DataManagerImpl)myDataManager).getDataContextTest(this);
  }

  protected void processMouseEvent(final MouseEvent e) {
    super.processMouseEvent(e);
    if (getLayoutPolicy() != AUTO_LAYOUT_POLICY) return;
  }

  protected void processMouseMotionEvent(final MouseEvent e) {
    super.processMouseMotionEvent(e);

    if (getLayoutPolicy() != AUTO_LAYOUT_POLICY) {
      return;
    }
    if (myAutoPopupRec != null && myAutoPopupRec.contains(e.getPoint())) {
      showAutoPopup();
    }
  }

  private void showAutoPopup() {
    if (isPopupShowing()) return;

    final ActionGroup group;
    if (myOrientation == SwingConstants.HORIZONTAL) {
      group = myActionGroup;
    }
    else {
      final DefaultActionGroup outside = new DefaultActionGroup();
      for (int i = myFirstOusideIndex; i < myVisibleActions.size(); i++) {
        outside.add(myVisibleActions.get(i));
      }
      group = outside;
    }

    myPopupToolbar = new PopupToolbar(myPlace, group, true, myDataManager, myActionManager, myKeymapManager) {
      protected void onOtherActionPerformed() {
        hidePopup();
      }

      protected DataContext getDataContext() {
        return ActionToolbarImpl.this.getDataContext();
      }
    };
    myPopupToolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);

    Point location;
    if (myOrientation == SwingConstants.HORIZONTAL) {
      location = getLocationOnScreen();
    }
    else {
      location = getLocationOnScreen();
      location.y = location.y + getHeight() - myPopupToolbar.getPreferredSize().height;
    }


    final ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(myPopupToolbar, null);
    builder.setResizable(false)
      .setRequestFocus(false)
      .setTitle(null)
      .setCancelOnClickOutside(true)
      .setCancelOnOtherWindowOpen(true)
      .setCancelCallback(new Computable<Boolean>() {
        public Boolean compute() {
          return myActionManager.isActionPopupStackEmpty();
        }
      })
      .setCancelOnMouseOutCallback(new MouseChecker() {
        public boolean check(final MouseEvent event) {
          if (myAutoPopupRec != null && myActionManager.isActionPopupStackEmpty()) {
            return !new RelativeRectangle(ActionToolbarImpl.this, myAutoPopupRec).contains(new RelativePoint(event));
          }
          return false;
        }
      });

    builder.addListener(new JBPopupListener() {
      public void onClosed(final JBPopup popup) {
        processClosed();
      }
    });
    myPopup = builder.createPopup();

    Disposer.register(myPopup, myPopupToolbar);

    myPopup.showInScreenCoordinates(this, location);

    final Window window = SwingUtilities.getWindowAncestor(this);
    if (window != null) {
      final ComponentAdapter componentAdapter = new ComponentAdapter() {
        public void componentResized(final ComponentEvent e) {
          hidePopup();
        }

        public void componentMoved(final ComponentEvent e) {
          hidePopup();
        }

        public void componentShown(final ComponentEvent e) {
          hidePopup();
        }

        public void componentHidden(final ComponentEvent e) {
          hidePopup();
        }
      };
      window.addComponentListener(componentAdapter);
      Disposer.register(myPopupToolbar, new Disposable() {
        public void dispose() {
          window.removeComponentListener(componentAdapter);
        }
      });
    }
  }


  private boolean isPopupShowing() {
    if (myPopup != null) {
      if (myPopup.getContent() != null) {
        return true;
      }
    }
    return false;
  }

  private void hidePopup() {
    if (myPopup != null) {
      myPopup.cancel();
      processClosed();
    }
  }

  private void processClosed() {
    if (myPopup == null) return;

    Disposer.dispose(myPopup);
    myPopup = null;
    updateActionsImmediately();
  }

  abstract static class PopupToolbar extends ActionToolbarImpl implements AnActionListener, Disposable {

    public PopupToolbar(final String place,
                        final ActionGroup actionGroup,
                        final boolean horizontal,
                        final DataManager dataManager,
                        final ActionManagerEx actionManager,
                        final KeymapManagerEx keymapManager) {
      super(place, actionGroup, horizontal, dataManager, actionManager, keymapManager);
      myActionManager.addAnActionListener(this);
    }

    public void dispose() {
      myActionManager.removeAnActionListener(this);
    }

    public void beforeActionPerformed(final AnAction action, final DataContext dataContext) {
    }

    public void afterActionPerformed(final AnAction action, final DataContext dataContext) {
      if (!myVisibleActions.contains(action)) {
        onOtherActionPerformed();
      }
    }

    protected abstract void onOtherActionPerformed();

    public void beforeEditorTyping(final char c, final DataContext dataContext) {
    }
  }


}
