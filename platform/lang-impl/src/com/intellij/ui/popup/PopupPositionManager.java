package com.intellij.ui.popup;

import com.intellij.codeInsight.lookup.LookupAdapter;
import com.intellij.codeInsight.lookup.LookupEvent;
import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.DimensionService;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author pegov
 */
public class PopupPositionManager {
  private PopupPositionManager() {
  }

  public static void positionPopupInBestPosition(final JBPopup hint, @Nullable final Editor editor) {
    final LookupEx lookup = LookupManager.getActiveLookup(editor);
    if (lookup != null && lookup.getCurrentItem() != null) {
      new PositionAdjuster(lookup.getComponent()).adjust(hint);
      lookup.addLookupListener(new LookupAdapter() {
        @Override
        public void lookupCanceled(LookupEvent event) {
          if (hint.isVisible()) {
            hint.cancel();
          }
        }
      });
      return;
    }

    if (editor != null && editor.getComponent().isShowing()) {
      hint.showInBestPositionFor(editor);
      return;
    }

    final PositionAdjuster positionAdjuster = createPositionAdjuster();
    if (positionAdjuster != null) positionAdjuster.adjust(hint);
  }

  private static Component discoverPopup(final DataKey<JBPopup> datakey, Component focusOwner) {
    if (focusOwner == null) {
      focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    }
    
    if (focusOwner == null) return null;
    
    final DataContext dataContext = DataManager.getInstance().getDataContext(focusOwner);
    if (dataContext == null) return null;
    
    final JBPopup popup = datakey.getData(dataContext);
    if (popup != null && popup.isVisible()) {
      return popup.getContent();
    }
    
    return null;
  }
  
  @Nullable
  private static PositionAdjuster createPositionAdjuster() {
    final Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (focusOwner == null) return null;
    
    if (focusOwner instanceof JBList) {
      return new PositionAdjuster(SwingUtilities.getWindowAncestor(focusOwner));
    }

    final Component existing = discoverPopup(LangDataKeys.CHOOSE_BY_NAME_DROPDOWN, focusOwner);
    return existing == null ? null : new PositionAdjuster2(existing, discoverPopup(LangDataKeys.CHOOSE_BY_NAME_POPUP, focusOwner));
  }
  
  private static class PositionAdjuster2 extends PositionAdjuster {

    private Component myTopComponent;

    private PositionAdjuster2(final Component relativeTo, final Component topComponent) {
      super(relativeTo);
      myTopComponent = topComponent;
    }

    @Override
    protected int getYForTopPositioning() {
      return myTopComponent.getLocationOnScreen().y;
    }
  }
  
  private static class PositionAdjuster {
    private static final int GAP = 5;    
    
    private Component myRelativeTo;
    private Point myRelativeOnScreen;
    private Rectangle myScreenRect;

    public PositionAdjuster(final Component relativeTo) {
      myRelativeTo = relativeTo;
      myRelativeOnScreen = relativeTo.getLocationOnScreen();

      GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
      //final DisplayMode displayMode = gd.getDisplayMode();
      final Toolkit toolkit = Toolkit.getDefaultToolkit();
      final Insets screenInsets = toolkit.getScreenInsets(gd.getDefaultConfiguration());

      myScreenRect = new Rectangle(screenInsets.left, screenInsets.top, toolkit.getScreenSize().width - screenInsets.left - screenInsets.right, 
                                   toolkit.getScreenSize().height - screenInsets.top - screenInsets.bottom);
    }
    
    protected Rectangle positionRight(final Dimension d) {
      return new Rectangle(myRelativeOnScreen.x + myRelativeTo.getWidth() + GAP, myRelativeOnScreen.y, d.width, 
                           d.height);
    }
    
    protected Rectangle positionLeft(final Dimension d) {
      return new Rectangle(myRelativeOnScreen.x - GAP - d.width, myRelativeOnScreen.y, d.width, d.height);
    }
    
    protected Rectangle positionAbove(final Dimension d) {
      return new Rectangle(myRelativeOnScreen.x, getYForTopPositioning() - GAP - d.height, d.width, d.height);
    }
    
    protected Rectangle positionUnder(final Dimension d) {
      return new Rectangle(myRelativeOnScreen.x, myRelativeOnScreen.y + GAP + myRelativeTo.getHeight(), d.width, d.height);
    }
    
    protected int getYForTopPositioning() {
      return myRelativeOnScreen.y;
    }
    
    /**
     * Try to position:
     * 1. to the right
     * 2. to the left
     * 3. above
     * 4. under
     * 
     * @param popup
     */
    public void adjust(final JBPopup popup) {
      final Dimension d = getPopupSize(popup);

      Rectangle popupRect = null;
      Rectangle r = positionRight(d);
      if (myScreenRect.contains(r)) {
        popupRect = r;
      } 
      
      if (popupRect == null) {
        r = positionLeft(d);
        if (myScreenRect.contains(r)) {
          popupRect = r;
        }
      } 
      
      if (popupRect == null) {
        r = positionAbove(d);
        if (myScreenRect.contains(r)) {
          popupRect = r;
        }
      } 
      
      if (popupRect == null) {
        r = positionUnder(d);
        if (myScreenRect.contains(r)) {
          popupRect = r;
        }
      } 
      
      if (popupRect != null) {
        final Point p = new Point(r.x - myRelativeOnScreen.x, r.y - myRelativeOnScreen.y);
        popup.show(new RelativePoint(myRelativeTo, p));
      } else {
        // ok, popup does not fit, will try to resize it
        final List<Rectangle> boxes = new ArrayList<Rectangle>();
        // right
        boxes.add(crop(myScreenRect, new Rectangle(myRelativeOnScreen.x + myRelativeTo.getWidth() + GAP, myRelativeOnScreen.y,
                                                   myScreenRect.width, myScreenRect.height)));
        
        // left
        boxes.add(crop(myScreenRect, new Rectangle(myScreenRect.x, myRelativeOnScreen.y, myRelativeOnScreen.x - myScreenRect.x - GAP,
                                                   myScreenRect.height)));
        
        // top
        boxes.add(crop(myScreenRect, new Rectangle(myRelativeOnScreen.x, myScreenRect.y,
                                                   myScreenRect.width, getYForTopPositioning() - myScreenRect.y - GAP)));
        
        // bottom
        boxes.add(crop(myScreenRect, new Rectangle(myRelativeOnScreen.x, myRelativeOnScreen.y + myRelativeTo.getHeight() + GAP,
                                                   myScreenRect.width, myScreenRect.height)));

        Collections.sort(boxes, new Comparator<Rectangle>() {
          @Override
          public int compare(final Rectangle o1, final Rectangle o2) {
            final int i = new Integer(o1.width).compareTo(o2.width);
            return i == 0 ? new Integer(o1.height).compareTo(o2.height) : i;
          }
        });

        final Rectangle suitableBox = boxes.get(boxes.size() - 1);
        final Rectangle crop = crop(suitableBox, 
                                    new Rectangle(suitableBox.x < myRelativeOnScreen.x ? suitableBox.x + suitableBox.width - d.width : 
                                      suitableBox.x, suitableBox.y < myRelativeOnScreen.y ? suitableBox.y + suitableBox.height - d.height : suitableBox.y, 
                                                  d.width, d.height));

        popup.setSize(crop.getSize());
        popup.show(new RelativePoint(myRelativeTo, new Point(crop.getLocation().x - myRelativeOnScreen.x,
                                                             crop.getLocation().y - myRelativeOnScreen.y)));
      }
    }
    
    private static Rectangle crop(final Rectangle source, final Rectangle toCrop) {
      final Rectangle result = new Rectangle(toCrop);
      if (toCrop.x < source.x) {
        result.width -= source.x - toCrop.x;
        result.x = source.x;
      }
      
      if (toCrop.y < source.y) {
        result.height -= source.y - toCrop.y;
        result.y = source.y;
      }
      
      if (result.x + result.width > source.x + source.width) {
        result.width = source.x + source.width - result.x; 
      }
      
      if (result.y + result.height > source.y + source.height) {
        result.height = source.y + source.height - result.y; 
      }

      return result;
    }
    
    protected static Dimension getPopupSize(final JBPopup popup) {
      Dimension size = null;
      if (popup instanceof AbstractPopup) {
        final String dimensionKey = ((AbstractPopup)popup).getDimensionServiceKey();
        if (dimensionKey != null) {
          size = DimensionService.getInstance().getSize(dimensionKey);
        }
      }

      if (size == null) {
        size = popup.getContent().getPreferredSize();
      }
      
      return size;
    } 
  }
}
