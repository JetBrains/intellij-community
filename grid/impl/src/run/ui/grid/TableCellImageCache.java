package com.intellij.database.run.ui.grid;

import com.intellij.database.run.ui.ResultViewWithRows;
import com.intellij.idea.AppMode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.*;
import com.intellij.util.SingleAlarm;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.SLRUMap;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.UIResource;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.ref.SoftReference;
import java.util.Map;
import java.util.Random;

public final class TableCellImageCache {
  public static final int MAX_ROWS = 100;
  public static final int MAX_COLUMNS = 30;
  private CacheImpl myImageCache;
  private Map<TableCellRenderer, CachingCellRendererWrapper> myCellRendererWrappers;

  private final ResultViewWithRows myTable;
  private int myCacheLevel;
  private boolean myWrappedGraphics;
  private final Random myRandom = new Random();
  private final boolean myCacheEnabled;

  public TableCellImageCache(@NotNull ResultViewWithRows table, @NotNull Disposable disposable) {
    myTable = table;
    myCacheEnabled = Registry.is("database.grid.render.cache");
    reset();
    UiNotifyConnector.installOn(myTable.getComponent(), new Activatable() {
      @Override
      public void hideNotify() {
        reset();
      }
    });
    installFastScrollingCaching(disposable);
  }

  private void installFastScrollingCaching(@NotNull Disposable disposable) {
    if (!myCacheEnabled || !Registry.is("database.grid.render.cache.fast.scroll")) return;
    final SingleAlarm[] alarmRef = {null};
    final SingleAlarm scrollingAlarm = new SingleAlarm(() -> {
      if (myCacheLevel == 0) {
        myTable.getComponent().repaint();
      }
      else {
        myCacheLevel -= 25;
        alarmRef[0].cancelAndRequest();
      }
    }, 100);
    alarmRef[0] = scrollingAlarm;
    Disposer.register(disposable, scrollingAlarm);
    final int[] xySum = {0};
    final ChangeListener scrollListener = (ChangeEvent e) -> {
      JScrollBar horizontalScrollBar = myTable.getHorizontalScrollBar();
      JScrollBar verticalScrollBar = myTable.getVerticalScrollBar();
      int curSum = -((horizontalScrollBar == null ? 0 : horizontalScrollBar.getValue()) +
                     (verticalScrollBar == null ? 0 : verticalScrollBar.getValue()));
      int delta = xySum[0] == 0 ? 0 : Math.abs(curSum - xySum[0]);
      xySum[0] = curSum;
      if (delta <= myTable.getRowHeight() * 4) return;
      scrollingAlarm.cancelAndRequest();
      myCacheLevel = 100;
    };
    UiNotifyConnector.Once.installOn(myTable.getComponent(), new Activatable() {
      @Override
      public void showNotify() {
        JScrollBar horizontalScrollBar = myTable.getHorizontalScrollBar();
        JScrollBar verticalScrollBar = myTable.getVerticalScrollBar();
        if (horizontalScrollBar != null) horizontalScrollBar.getModel().addChangeListener(scrollListener);
        if (verticalScrollBar != null) verticalScrollBar.getModel().addChangeListener(scrollListener);
      }
    });
  }

  public TableCellRenderer wrapCellRenderer(@NotNull TableCellRenderer renderer) {
    if (!myCacheEnabled) return renderer;
    return myCellRendererWrappers.get(renderer);
  }

  public void reset() {
    myImageCache = new CacheImpl();
    myCellRendererWrappers = FactoryMap.createMap(CachingCellRendererWrapper::new, () -> new Reference2ObjectOpenHashMap<>());
  }

  public boolean isCacheEnabled() {
    return myCacheEnabled;
  }

  public void adjustCacheSize(int size) {
    if (myCacheEnabled) {
      myImageCache.adjustCacheSize(size);
    }
  }

  private static @NotNull Object getState(@NotNull Component renderer) {
    Color bg = hashSafeColor(renderer.getBackground());
    Color fg = hashSafeColor(renderer.getForeground());
    Boolean opaque = renderer.isOpaque() ? Boolean.TRUE : Boolean.FALSE;
    return Trinity.create(bg, fg, opaque);
  }

  private static @Nullable Color hashSafeColor(@Nullable Color color) {
    Class<? extends Color> colorClass = color != null ? color.getClass() : null;
    if (colorClass == null || colorClass == Color.class || colorClass == ColorUIResource.class) return color;
    //noinspection UseJBColor
    return new Color(color.getRed(), color.getBlue(), color.getGreen(), color.getAlpha());
  }

  public class CachingCellRendererWrapper extends CellRendererPanel implements TableCellRenderer {
    final TableCellRenderer myWrappee;

    Object myValue;
    boolean myIsSelected;
    boolean myHasFocus;

    CachingCellRendererWrapper(@NotNull TableCellRenderer wrappee) {
      myWrappee = wrappee;
    }

    public void clearCache() {
      myImageCache.clear();
    }

    public @NotNull TableCellRenderer getDelegate() {
      return myWrappee;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      myValue = value;
      myIsSelected = isSelected;
      myHasFocus = hasFocus;

      Component originalComponent = myWrappee.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

      if (!hasWrappeeComponent()) {
        add(originalComponent);
      }
      else if (originalComponent != getWrappeeComponent()) {
        // removing/adding components is an expensive operation
        // if this code is reached, you're most probably using
        // cell renderers which re-create their component for rendering
        removeAll();
        add(originalComponent);
      }

      return this;
    }

    @Override
    public AccessibleContext getAccessibleContext() {
      return getWrappeeComponent().getAccessibleContext();
    }

    @Override
    protected void paintChildren(Graphics g) {
      boolean wrappedGraphics = g instanceof Graphics2DDelegate; // background image, or other tweaks
      JComponent originalComponent = getWrappeeComponent();
      originalComponent.validate();

      // when the background image is present (the graphics is wrapped):
      // use cache when scrolling fast, otherwise paint high-quality lcd-AA text
      // which is not available when painting transparent off-screen images
      boolean forceNoCaching = wrappedGraphics && (myCacheLevel == 0 || myCacheLevel < 100 && myRandom.nextInt(100) > myCacheLevel) ||
                               AppMode.isRemoteDevHost();

      Rectangle visibleRect = myTable.getComponent().getVisibleRect();
      int width = getWidth();
      int height = getHeight();

      boolean paintingExpansionHint = getParent() instanceof ExpandedItemRendererComponentWrapper;
      boolean tooLargeToCache = visibleRect.width <= width || visibleRect.height <= height;
      if (paintingExpansionHint ||
          tooLargeToCache ||
          forceNoCaching ||
          UIUtil.isPrinting(g)) {
        super.paintChildren(g);
        return;
      }
      if (myWrappedGraphics != wrappedGraphics) {
        myImageCache.clear();
      }
      myWrappedGraphics = wrappedGraphics;

      Graphics2D g2d = (Graphics2D)g;
      boolean isMacRetina = UIUtil.isRetina(g2d);

      Object userCellState = getState(this);
      CellStateInfo cacheKey = new CellStateInfo(this, myValue, myIsSelected, myHasFocus, width, height, isMacRetina, userCellState);
      BufferedImage image = myImageCache.get(cacheKey);
      if (image == null) {
        int type = wrappedGraphics ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        image = ImageUtil.createImage(g2d, width, height, type);
        paintComponentOn(originalComponent, image);
        myImageCache.put(cacheKey, image);
      }
      StartupUiUtil.drawImage(g, image, null, 0, 0);
    }

    @Override
    public Border getBorder() {
      return hasWrappeeComponent() ? getWrappeeBorder() : super.getBorder();
    }

    @Override
    public void setBorder(Border border) {
      if (hasWrappeeComponent() && !(border instanceof UIResource)) {
        getWrappeeComponent().setBorder(border);
        return;
      }
      super.setBorder(border);
    }

    @Override
    public void setBackground(Color bg) {
      if (hasWrappeeComponent()) getWrappeeComponent().setBackground(bg);
      super.setBackground(bg);
    }

    private Border getWrappeeBorder() {
      Border border = getWrappeeComponent().getBorder();
      return border instanceof UIResource ? super.getBorder() : border;
    }

    private void paintComponentOn(@NotNull JComponent c, @NotNull BufferedImage image) {
      boolean hasAlpha = image.getColorModel().hasAlpha();
      boolean wasDoubleBuffered = RepaintManager.currentManager(c).isDoubleBufferingEnabled();
      RepaintManager.currentManager(c).setDoubleBufferingEnabled(false);
      c.setOpaque(true);
      c.setBackground(myIsSelected || !hasAlpha ? c.getBackground() : Gray.TRANSPARENT);
      if (c instanceof CellRendererPanel) ((CellRendererPanel)c).setSelected(myIsSelected || !hasAlpha);

      Graphics2D g = image.createGraphics();
      try {
        c.paint(g);
      }
      finally {
        g.dispose();
      }

      if (wasDoubleBuffered) {
        RepaintManager.currentManager(c).setDoubleBufferingEnabled(wasDoubleBuffered);
      }
    }

    private JComponent getWrappeeComponent() {
      return (JComponent)getComponent(0);
    }

    private boolean hasWrappeeComponent() {
      return getComponentCount() > 0;
    }
  }

  private static final class CellStateInfo {
    final TableCellRenderer myRenderer; // use identity comparison only
    final Object myValue;
    final boolean myIsSelected;
    final boolean myHasFocus;
    final int myWidth;
    final int myHeight;
    final boolean myIsRetina;
    final Object myUserState;

    CellStateInfo(@NotNull TableCellRenderer renderer, @Nullable Object value,
                  boolean isSelected, boolean hasFocus,
                  int width, int height, boolean isRetina,
                  @Nullable Object userState) {
      myRenderer = renderer;
      myValue = value;
      myIsSelected = isSelected;
      myHasFocus = hasFocus;
      myWidth = width;
      myHeight = height;
      myIsRetina = isRetina;
      myUserState = userState;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      CellStateInfo info = (CellStateInfo)o;

      if (myIsSelected != info.myIsSelected) return false;
      if (myHasFocus != info.myHasFocus) return false;
      if (myWidth != info.myWidth) return false;
      if (myHeight != info.myHeight) return false;
      if (myRenderer != info.myRenderer) return false;
      if (!equalObjectsAndHashCodes(myValue, info.myValue)) return false;
      if (myIsRetina != info.myIsRetina) return false;
      if (!equalObjectsAndHashCodes(myUserState, info.myUserState)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = System.identityHashCode(myRenderer);
      result = 31 * result + (myValue != null ? myValue.hashCode() : 0);
      result = 31 * result + (myIsSelected ? 1 : 0);
      result = 31 * result + (myHasFocus ? 1 : 0);
      result = 31 * result + myWidth;
      result = 31 * result + myHeight;
      result = 31 * result + (myIsRetina ? 1 : 0);
      result = 31 * result + (myUserState != null ? myUserState.hashCode() : 0);
      return result;
    }

    // prevents EA-70727 (IAE: TObjectHash.throwObjectContractViolation)
    private static boolean equalObjectsAndHashCodes(@Nullable Object o1, @Nullable Object o2) {
      return o1 == null && o2 == null ||
             o1 != null && o2 != null && o1.equals(o2) && o1.hashCode() == o2.hashCode();
    }
  }

  private static final class CacheImpl {
    private SLRUMap<CellStateInfo, SoftReference<BufferedImage>> myCache = new SLRUMap<>(0, 0);
    private int myCacheSize = 0;

    public @Nullable BufferedImage get(@NotNull CellStateInfo key) {
      SoftReference<BufferedImage> imageRef = myCache.get(key);
      if (imageRef == null) return null;

      BufferedImage image = imageRef.get();
      if (image == null) {
        myCache.remove(key);
      }
      return image;
    }

    public void put(@NotNull CellStateInfo key, @NotNull BufferedImage value) {
      myCache.put(key, new SoftReference<>(value));
    }

    public void clear() {
      myCache.clear();
    }

    public void adjustCacheSize(int newMaxSize) {
      int oldCacheSize = myCacheSize;
      if (oldCacheSize >= newMaxSize && oldCacheSize / 2 < newMaxSize) {
        return;
      }

      var newCache = new SLRUMap<CellStateInfo, SoftReference<BufferedImage>>(newMaxSize, newMaxSize);
      for (Map.Entry<CellStateInfo, SoftReference<BufferedImage>> entry : myCache.entrySet()) {
        newCache.put(entry.getKey(), entry.getValue());
      }
      myCache = newCache;
      myCacheSize = newMaxSize;
    }
  }
}
