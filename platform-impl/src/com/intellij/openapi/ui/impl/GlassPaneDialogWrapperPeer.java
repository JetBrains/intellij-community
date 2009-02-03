package com.intellij.openapi.ui.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.impl.TypeSafeDataProviderAdapter;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.TypeSafeDataProvider;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.DialogWrapperDialog;
import com.intellij.openapi.ui.DialogWrapperPeer;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.IdeGlassPaneEx;
import com.intellij.ui.FocusTrackback;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.popup.StackingPopupDispatcherImpl;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.ref.WeakReference;
import java.util.List;

/**
 * @author spleaner
 */
public class GlassPaneDialogWrapperPeer extends DialogWrapperPeer implements FocusTrackbackProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.ui.impl.GlassPaneDialogWrapperPeer");

  private DialogWrapper myWrapper;
  private WindowManagerEx myWindowManager;
  private Project myProject;
  private MyDialog myDialog;
  private boolean myCanBeParent;

  public GlassPaneDialogWrapperPeer(DialogWrapper wrapper, Project project, boolean canBeParent) throws GlasspanePeerUnavailableException {
    myWrapper = wrapper;
    myCanBeParent = canBeParent;

    myWindowManager = null;
    Application application = ApplicationManager.getApplication();
    if (application != null && application.hasComponent(WindowManager.class)) {
      myWindowManager = (WindowManagerEx)WindowManager.getInstance();
    }

    Window window = null;
    if (myWindowManager != null) {

      if (project == null) {
        project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
      }

      myProject = project;

      window = myWindowManager.suggestParentWindow(project);
      if (window == null) {
        Window focusedWindow = myWindowManager.getMostRecentFocusedWindow();
        if (focusedWindow instanceof IdeFrameImpl) {
          window = focusedWindow;
        }
      }
    }

    Window owner;
    if (window != null) {
      owner = window;
    }
    else {
      owner = JOptionPane.getRootFrame();
    }

    createDialog(owner);
  }

  public GlassPaneDialogWrapperPeer(DialogWrapper wrapper, boolean canBeParent) throws GlasspanePeerUnavailableException {
    this(wrapper, (Project)null, canBeParent);
  }

  public GlassPaneDialogWrapperPeer(DialogWrapper wrapper, @NotNull Component parent, boolean canBeParent)
    throws GlasspanePeerUnavailableException {
    myWrapper = wrapper;
    myCanBeParent = canBeParent;
    if (!parent.isShowing() && parent != JOptionPane.getRootFrame()) {
      throw new IllegalArgumentException("parent must be showing: " + parent);
    }
    myWindowManager = null;
    Application application = ApplicationManager.getApplication();
    if (application != null && application.hasComponent(WindowManager.class)) {
      myWindowManager = (WindowManagerEx)WindowManager.getInstance();
    }

    Window owner = parent instanceof Window ? (Window)parent : (Window)SwingUtilities.getAncestorOfClass(Window.class, parent);
    if (!(owner instanceof Dialog) && !(owner instanceof Frame)) {
      owner = JOptionPane.getRootFrame();
    }

    createDialog(owner);
  }

  private void createDialog(final Window owner) throws GlasspanePeerUnavailableException {
    if (owner instanceof IdeFrame) {
      final JFrame frame = (JFrame)owner;
      final JComponent glassPane = (JComponent)frame.getGlassPane();

      assert glassPane instanceof IdeGlassPaneEx : "GlassPane should be instance of IdeGlassPane!";
      myDialog = new MyDialog((IdeGlassPaneEx)glassPane, myWrapper, myProject);
    }
    else {
      throw new GlasspanePeerUnavailableException();
    }
  }

  public FocusTrackback getFocusTrackback() {
    return myDialog.getFocusTrackback();
  }

  public void setUndecorated(final boolean undecorated) {
    LOG.assertTrue(undecorated, "Decorated dialogs are not supported!");
  }

  public void addMouseListener(final MouseListener listener) {
    throw new UnsupportedOperationException("Not implemented in " + getClass().getCanonicalName());
  }

  public void addMouseListener(final MouseMotionListener listener) {
    throw new UnsupportedOperationException("Not implemented in " + getClass().getCanonicalName());
  }

  public void addKeyListener(final KeyListener listener) {
    throw new UnsupportedOperationException("Not implemented in " + getClass().getCanonicalName());
  }

  public void toFront() {
    throw new UnsupportedOperationException("Not implemented in " + getClass().getCanonicalName());
  }

  public void toBack() {
    throw new UnsupportedOperationException("Not implemented in " + getClass().getCanonicalName());
  }

  public void dispose() {
    LOG.assertTrue(EventQueue.isDispatchThread(), "Access is allowed from event dispatch thread only");

    if (myDialog != null) {
      Disposer.dispose(myDialog);
      myDialog = null;
      myProject = null;
      myWindowManager = null;
    }
  }

  public Container getContentPane() {
    return myDialog.getContentPane();
  }

  public Window getOwner() {
    throw new UnsupportedOperationException("Not implemented in " + getClass().getCanonicalName());
  }

  public Window getWindow() {
    throw new UnsupportedOperationException("Not implemented in " + getClass().getCanonicalName());
  }

  public JRootPane getRootPane() {
    return myDialog.getRootPane();
  }

  public Dimension getSize() {
    return myDialog.getSize();
  }

  public String getTitle() {
    return "";
  }

  public Dimension getPreferredSize() {
    return myDialog.getPreferredSize();
  }

  public void setModal(final boolean modal) {
    LOG.assertTrue(modal, "Can't be non modal!");
  }

  public boolean isVisible() {
    return myDialog.isVisible();
  }

  public boolean isShowing() {
    return myDialog.isShowing();
  }

  public void setSize(final int width, final int height) {
    myDialog.setSize(width, height);
  }

  public void setTitle(final String title) {
    throw new UnsupportedOperationException("Not implemented in " + getClass().getCanonicalName());
  }

  // TODO: WTF?! VOID?!!!
  public void isResizable() {
  }

  public void setResizable(final boolean resizable) {
    throw new UnsupportedOperationException("Not implemented in " + getClass().getCanonicalName());
  }

  public Point getLocation() {
    return myDialog.getLocation();
  }

  public void setLocation(final Point p) {
    setLocation(p.x, p.y);
  }

  public void setLocation(final int x, final int y) {
    if (myDialog == null || !myDialog.isShowing()) {
      return;
    }

    final Point _p = new Point(x, y);
    final JRootPane pane = SwingUtilities.getRootPane(myDialog);

    SwingUtilities.convertPointFromScreen(_p, pane);

    final Insets insets = myDialog.getInsets();

    // todo: fix coords to include shadow (border) paddings
    // todo: reimplement dragging in every client to calculate window position properly
    int _x = _p.x - insets.left;
    int _y = _p.y - insets.top;

    final Container container = myDialog.getTransparentPane();

    _x = _x > 0 ? (_x + myDialog.getWidth() < container.getWidth() ? _x : container.getWidth() - myDialog.getWidth()) : 0;
    _y = _y > 0 ? (_y + myDialog.getHeight() < container.getHeight() ? _y : container.getHeight() - myDialog.getHeight()) : 0;

    myDialog.setLocation(_x, _y);
  }

  public void show() {
    LOG.assertTrue(EventQueue.isDispatchThread(), "Access is allowed from event dispatch thread only");

    hidePopupsIfNeeded();

    myDialog.setVisible(true);
  }

  public void setContentPane(final JComponent content) {
    myDialog.setContentPane(content);
  }

  public void centerInParent() {
    myDialog.center();
  }

  public void validate() {
    myDialog.validate();
  }

  public void repaint() {
    myDialog.repaint();
  }

  public void pack() {
  }

  public void setIconImages(final List<Image> image) {
    throw new UnsupportedOperationException("Not implemented in " + getClass().getCanonicalName());
  }

  public void setAppIcons() {
    throw new UnsupportedOperationException("Not implemented in " + getClass().getCanonicalName());
  }

  //[kirillk] for now it only deals with the TaskWindow under Mac OS X: modal dialogs are shown behind JBPopup
  //hopefully this whole code will go away
  private void hidePopupsIfNeeded() {
    if (!SystemInfo.isMac) return;

    StackingPopupDispatcherImpl.getInstance().hidePersistentPopups();

    Disposer.register(myDialog, new Disposable() {
      public void dispose() {
        StackingPopupDispatcherImpl.getInstance().restorePersistentPopups();
      }
    });
  }

  private static class MyDialog extends JPanel implements Disposable, DialogWrapperDialog, DataProvider, FocusTrackback.Provider {
    private final WeakReference<DialogWrapper> myDialogWrapper;
    private final IdeGlassPaneEx myPane;
    private final WeakReference<Project> myProject;
    private JComponent myContentPane;
    private final MyRootPane myRootPane;
    private Point myLocation;
    private boolean myForceRelayout;
    private BufferedImage shadow;
    private final Container myTransparentPane;
    private boolean myManualLocation;
    private JButton myDefaultButton;

    private MyDialog(IdeGlassPaneEx pane, DialogWrapper wrapper, Project project) {
      setLayout(new BorderLayout());
      setOpaque(false);
      setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

      myPane = pane;
      myDialogWrapper = new WeakReference<DialogWrapper>(wrapper);
      myProject = new WeakReference<Project>(project);

      myRootPane = new MyRootPane(this); // be careful with DialoWrapper.dispose()!

      myContentPane = new JPanel();
      myContentPane.setOpaque(true);
      add(myContentPane, BorderLayout.CENTER);

      myTransparentPane = createTransparentPane();

      setFocusCycleRoot(true);
    }

    public Container getTransparentPane() {
      return myTransparentPane;
    }

    @Override
    public void setVisible(final boolean show) {
      if (show) {
        myPane.add(myTransparentPane);
        myTransparentPane.add(this);
        myTransparentPane.setVisible(true);
      }

      super.setVisible(show);

      if (show) {
        ((JComponent)myTransparentPane).revalidate();
        myTransparentPane.repaint();
      } else {
        myTransparentPane.remove(this);
        ((JComponent)myTransparentPane).revalidate();
        myTransparentPane.repaint();

        myTransparentPane.setVisible(false);
        myPane.remove(myTransparentPane);
      }
    }

    @Override
    public void paint(final Graphics g) {
      UIUtil.applyRenderingHints(g);
      super.paint(g);
    }

    private static Container createTransparentPane() {
      final JPanel result = new JPanel() {
        @Override
        public void addNotify() {
          final Container container = getParent();
          if (container != null) {
            setBounds(0, 0, container.getWidth(), container.getHeight());
          }

          super.addNotify();
        }
      };
      result.setLayout(null);
      result.setOpaque(false);

      // to not pass events through transparent pane
      result.addMouseListener(new MouseAdapter() {});
      result.addMouseMotionListener(new MouseMotionAdapter() {});

      return result;
    }

    @Override
    protected void paintComponent(final Graphics g) {
      final Graphics2D g2 = (Graphics2D)g;
      if (shadow != null) {
        g2.drawImage(shadow, 0, 0, null);
      }

      super.paintComponent(g);
    }

    @Override
    public void doLayout() {
      _layout(myForceRelayout);
      super.doLayout();
    }

    @Override
    public void validate() {
      myForceRelayout = true;
      super.validate();
    }

    @Override
    public void setLocation(final int x, final int y) {
      myManualLocation = true;
      super.setLocation(x, y);
    }

    private void _layout(final boolean force) {
      if (myLocation == null || force) {
        final Dimension s = getPreferredSize();

        final Container parent = SwingUtilities.getRootPane(this);
        if (parent != null) {
          final Dimension pd = parent.getSize();

          final DialogWrapper dialogWrapper = myDialogWrapper.get();

          final int width = (int)(s.width * dialogWrapper.getHorizontalStretch());
          final int height = (int)(s.height * dialogWrapper.getVerticalStretch());

          if (myManualLocation) {
            final Point location = getLocation();
            final int x = location.x + s.width > pd.width ? pd.width - s.width : location.x;
            final int y = location.y + s.height > pd.height ? pd.height - s.height : location.y;
            myLocation = new Point(x, y);
            setBounds(x, y, width, height);
          }
          else {
            myLocation = new Point((pd.width - s.width) / 2, (pd.height - s.height) / 2);
            setBounds(myLocation.x, myLocation.y, width, height);
          }
        }

        createShadow();
        myForceRelayout = false;
      }
    }

    private void createShadow() {
      int w = getWidth() - 20;
      int h = getHeight() - 20;
      int shadowSize = 10;

      shadow = GraphicsUtilities.createCompatibleTranslucentImage(w, h);
      Graphics2D g2 = shadow.createGraphics();
      g2.setColor(Color.WHITE);
      g2.fillRect(0, 0, w, h);
      g2.dispose();

      ShadowRenderer renderer = new ShadowRenderer(shadowSize, 0.4f, Color.BLACK);
      shadow = renderer.createShadow(shadow);
    }

    public void dispose() {
      remove(getContentPane());
      repaint();

      final Runnable disposer = new Runnable() {
        public void run() {
          setVisible(false);
        }
      };

      if (EventQueue.isDispatchThread()) {
        disposer.run();
      }
      else {
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(disposer);
      }
    }

    public void setContentPane(JComponent content) {
      if (myContentPane != null) {
        remove(myContentPane);
        myContentPane = null;
      }

      myContentPane = content;
      myContentPane.setOpaque(true); // should be opaque
      add(myContentPane, BorderLayout.CENTER);
    }

    public JComponent getContentPane() {
      return myContentPane;
    }

    public JRootPane getRootPane() {
      return myRootPane;
    }

    public DialogWrapper getDialogWrapper() {
      return myDialogWrapper.get();
    }

    public Object getData(@NonNls final String dataId) {
      final DialogWrapper wrapper = myDialogWrapper.get();
      if (wrapper instanceof DataProvider) {
        return ((DataProvider)wrapper).getData(dataId);
      }
      else if (wrapper instanceof TypeSafeDataProvider) {
        TypeSafeDataProviderAdapter adapter = new TypeSafeDataProviderAdapter((TypeSafeDataProvider)wrapper);
        return adapter.getData(dataId);
      }
      return null;
    }

    public void setSize(int width, int height) {
      Point location = getLocation();
      Rectangle rect = new Rectangle(location.x, location.y, width, height);
      ScreenUtil.fitToScreen(rect);
      if (location.x != rect.x || location.y != rect.y) {
        setLocation(rect.x, rect.y);
      }

      super.setSize(rect.width, rect.height);
    }

    public void setBounds(int x, int y, int width, int height) {
      Rectangle rect = new Rectangle(x, y, width, height);
      //ScreenUtil.fitToScreen(rect);
      super.setBounds(rect.x, rect.y, rect.width, rect.height);
    }

    public void setBounds(Rectangle r) {
      //ScreenUtil.fitToScreen(r);
      super.setBounds(r);
    }

    public FocusTrackback getFocusTrackback() {
      return null;
    }

    public void center() {
      myManualLocation = false;
      _layout(true);
    }

    public void setDefaultButton(final JButton defaultButton) {
      //((JComponent)myPane).getRootPane().setDefaultButton(defaultButton);
      myDefaultButton = defaultButton;
    }
  }

  private static class MyRootPane extends JRootPane {
    private final MyDialog myDialog;

    private MyRootPane(final MyDialog dialog) {
      myDialog = dialog;
    }

    @Override
    public void registerKeyboardAction(final ActionListener anAction,
                                       final String aCommand,
                                       final KeyStroke aKeyStroke,
                                       final int aCondition) {
      myDialog.registerKeyboardAction(anAction, aCommand, aKeyStroke, aCondition);
    }

    @Override
    public void unregisterKeyboardAction(final KeyStroke aKeyStroke) {
      myDialog.unregisterKeyboardAction(aKeyStroke);
    }

    @Override
    public void setDefaultButton(final JButton defaultButton) {
      myDialog.setDefaultButton(defaultButton);
    }
  }

  public static class GlasspanePeerUnavailableException extends Exception {
  }
}
