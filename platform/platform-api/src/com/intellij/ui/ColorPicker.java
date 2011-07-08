/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.PairFunction;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.ColorModel;
import java.awt.image.MemoryImageSource;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author pegov
 */
public class ColorPicker extends JPanel implements Consumer<Color>, DocumentListener {
  public static final Icon PICK = IconLoader.findIcon("/toolbar/unknown.png");
  private static final String COLOR_CHOOSER_COLORS_KEY = "ColorChooser.RecentColors";

  private Color myColor;
  private ColorPreviewComponent myPreviewComponent;
  private final ColorWheelPanel myColorWheelPanel;

  private boolean myAutoUpdate = false;

  private RecentColorsComponent myRecentColorsComponent;

  public ColorPicker() {
    this(null);
  }

  public ColorPicker(@Nullable Color color) {
    this(color, true);
  }  

  public ColorPicker(@Nullable Color color, boolean restoreColors) {
    setLayout(new BorderLayout());
    setBorder(BorderFactory.createEmptyBorder(5, 5, 0, 5));

    myColorWheelPanel = new ColorWheelPanel(this);

    try {
      add(buildTopPanel(this), BorderLayout.NORTH);
      add(myColorWheelPanel, BorderLayout.CENTER);


      myRecentColorsComponent = new RecentColorsComponent(new Consumer<Color>() {
        @Override
        public void consume(Color color) {
          ColorPicker.this.consume(color);
          myColorWheelPanel.setColor(color);
        }
      }, restoreColors);

      add(myRecentColorsComponent, BorderLayout.SOUTH);
    }
    catch (ParseException e) {
      // hell
    }

    if (color != null) {
      consume(color);
      myColorWheelPanel.setColor(color);
    }

    setSize(300, 350);
  }

  public void appendRecentColor() {
    myRecentColorsComponent.appendColor(myColor);
  }

  public void saveRecentColors() {
    myRecentColorsComponent.saveColors();
  }

  public Color getColor() {
    return myColor;
  }

  @Override
  public void insertUpdate(DocumentEvent e) {
    if (!myAutoUpdate) {
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          validateAndUpdatePreview();
        }
      });
    }
  }

  @Override
  public void removeUpdate(DocumentEvent e) {
    if (!myAutoUpdate) {
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          validateAndUpdatePreview();
        }
      });
    }
  }

  @Override
  public void changedUpdate(DocumentEvent e) {
    // ignore
  }

  private void validateAndUpdatePreview() {
    forEveryKey(new PairFunction<JTextField, Pair<String, String>, Boolean>() {
                  @Override
                  public Boolean fun(JTextField field, Pair<String, String> pair) {
                    if (field.hasFocus()) {
                      final String key = pair.getFirst();
                      if ("hex".equals(key)) {
                        // updating preview from hex

                        Color color = null;
                        final String str = pair.getSecond();
                        if (str.length() == 3) {
                          color = new Color(
                            17 * Integer.valueOf(String.valueOf(str.charAt(0)), 16).intValue(),
                            17 * Integer.valueOf(String.valueOf(str.charAt(1)), 16).intValue(),
                            17 * Integer.valueOf(String.valueOf(str.charAt(2)), 16).intValue());
                        }
                        else if (str.length() == 6) {
                          color = Color.decode("0x" + str);
                        }

                        if (color != null) {
                          updatePreview(color, true);
                        }
                      }
                      else {
                        final Color color = gatherRGB();
                        if (color != null) {
                          updatePreview(color, false);
                        }
                      }

                      return true;
                    }

                    return false;
                  }
                }, this, new Function<String, Boolean>() {
      @Override
      public Boolean fun(String s) {
        return true;
      }
    }
    );
  }

  private void updatePreview(Color color, boolean fromHex) {
    myPreviewComponent.setColor(color);
    myColorWheelPanel.setColor(color);

    myColor = color;

    if (fromHex) {
      applyColorToRGB(color);
    }
    else {
      applyColorToHEX(color);
    }
  }

  @Override
  public void consume(Color color) {
    applyColorToRGB(color);
    applyColorToHEX(color);
    myPreviewComponent.setColor(color);

    myColor = color;
  }

  @Nullable
  private Color gatherRGB() {
    final HashMap<String, Integer> values = new HashMap<String, Integer>();
    _gatherRgb(values, this);

    if (values.size() == 3) {
      final Integer red = values.get("red");
      final Integer green = values.get("green");
      final Integer blue = values.get("blue");

      if (red != null && green != null && blue != null) {
        return new Color(red, green, blue);
      }
    }

    return null;
  }

  private void applyColorToHEX(final Color c) {
    try {
      myAutoUpdate = true;
      forEveryKey(new PairFunction<JTextField, Pair<String, String>, Boolean>() {
                    @Override
                    public Boolean fun(JTextField _c, Pair<String, String> pair) {
                      final String R = Integer.toHexString(c.getRed());
                      final String G = Integer.toHexString(c.getGreen());
                      final String B = Integer.toHexString(c.getBlue());
                      _c.setText(new StringBuffer()
                                   .append(R.length() < 2 ? "0" : "").append(R)
                                   .append(G.length() < 2 ? "0" : "").append(G)
                                   .append(B.length() < 2 ? "0" : "").append(B)
                                   .toString());
                      return true;
                    }
                  }, this, new Function<String, Boolean>() {
        @Override
        public Boolean fun(String s) {
          return "hex".equals(s);
        }
      }
      );
    }
    finally {
      myAutoUpdate = false;
    }
  }

  private void applyColorToRGB(final Color color) {
    try {
      myAutoUpdate = true;
      forEveryKey(new PairFunction<JTextField, Pair<String, String>, Boolean>() {
                    @Override
                    public Boolean fun(JTextField c, Pair<String, String> pair) {
                      final String key = pair.getFirst();
                      if ("red".equals(key)) {
                        c.setText(Integer.toString(color.getRed()));
                      }
                      else if ("green".equals(key)) {
                        c.setText(Integer.toString(color.getGreen()));
                      }
                      else if ("blue".equals(key)) {
                        c.setText(Integer.toString(color.getBlue()));
                      }

                      return false;
                    }
                  }, this, new Function<String, Boolean>() {
        @Override
        public Boolean fun(String s) {
          return "red".equals(s) || "green".equals(s) || "blue".equals(s);
        }
      }
      );
    }
    finally {
      myAutoUpdate = false;
    }
  }

  public static Color showDialog(Component parent, String caption, Color preselectedColor) {
    final ColorPickerDialog dialog = new ColorPickerDialog(parent, caption, preselectedColor);
    dialog.show();
    if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      return dialog.getColor();
    }

    return null;
  }

  private static void _gatherRgb(final Map<String, Integer> map, JComponent c) {
    forEveryKey(new PairFunction<JTextField, Pair<String, String>, Boolean>() {
                  @Override
                  public Boolean fun(JTextField c, Pair<String, String> pair) {
                    if (map.size() == 3) return true;
                    final String text = pair.getSecond();
                    int value;
                    try {
                      value = Integer.parseInt(text.length() > 3 ? text.substring(0, 3) : text);
                    }
                    catch (NumberFormatException e) {
                      value = 0;
                    }

                    map.put(pair.getFirst(), value > 255 ? 255 : value);
                    return false;
                  }
                }, c, new Function<String, Boolean>() {
      @Override
      public Boolean fun(String s) {
        return "red".equals(s) || "green".equals(s) || "blue".equals(s);
      }
    }
    );
  }

  private static boolean forEveryKey(PairFunction<JTextField, Pair<String, String>, Boolean> fun, JComponent c,
                                     Function<String, Boolean> filter) {
    if (c instanceof JTextField) {
      final String key = (String)c.getClientProperty("_key");
      if (key != null && filter.fun(key)) {
        final String text = ((JTextField)c).getText();
        if (fun.fun((JTextField)c, Pair.create(key, text))) {
          return true;
        }
      }
    }
    else {
      final Component[] components = c.getComponents();
      for (Component component : components) {
        if (component instanceof JComponent) {
          if (forEveryKey(fun, (JComponent)component, filter)) {
            return true;
          }
        }
      }
    }

    return false;
  }

  private JComponent buildTopPanel(DocumentListener l) throws ParseException {
    final JPanel result = new JPanel(new BorderLayout());
    //result.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

    final JPanel previewPanel = new JPanel(new BorderLayout());
    //previewPanel.add(new JButton(PICK), BorderLayout.WEST);
    myPreviewComponent = new ColorPreviewComponent();
    //myPreviewComponent.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
    previewPanel.add(myPreviewComponent, BorderLayout.CENTER);

    result.add(previewPanel, BorderLayout.NORTH);

    final JPanel rgbPanel = new JPanel();
    rgbPanel.setLayout(new BoxLayout(rgbPanel, BoxLayout.X_AXIS));
    rgbPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
    rgbPanel.add(new JLabel("R:"));
    rgbPanel.add(Box.createHorizontalStrut(2));
    final JTextField r = new JTextField(new NumberDocument(), "", 3);
    r.putClientProperty("_key", "red");
    r.getDocument().addDocumentListener(l);
    rgbPanel.add(r);
    rgbPanel.add(Box.createHorizontalStrut(5));
    rgbPanel.add(new JLabel("G:"));
    rgbPanel.add(Box.createHorizontalStrut(2));
    final JTextField g = new JTextField(new NumberDocument(), "", 3);
    g.putClientProperty("_key", "green");
    g.setColumns(3);
    g.getDocument().addDocumentListener(l);
    rgbPanel.add(g);
    rgbPanel.add(Box.createHorizontalStrut(5));
    rgbPanel.add(new JLabel("B:"));
    rgbPanel.add(Box.createHorizontalStrut(2));
    final JTextField b = new JTextField(new NumberDocument(), "", 3);
    b.putClientProperty("_key", "blue");
    b.setColumns(3);
    b.getDocument().addDocumentListener(l);
    rgbPanel.add(b);

    result.add(rgbPanel, BorderLayout.WEST);

    final JPanel hexPanel = new JPanel();
    hexPanel.setLayout(new BoxLayout(hexPanel, BoxLayout.X_AXIS));
    hexPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
    hexPanel.add(new JLabel("#:"));
    hexPanel.add(Box.createHorizontalStrut(3));
    final JTextField hex = new JTextField(new NumberDocument(true), "", 6);
    hex.putClientProperty("_key", "hex");
    hex.setColumns(6);
    hex.getDocument().addDocumentListener(l);
    hexPanel.add(hex);

    result.add(hexPanel, BorderLayout.EAST);

    return result;
  }

  public static void main(String[] args) {
    final JFrame frame = new JFrame();

    frame.getContentPane().add(new ColorPicker(new Color(255, 0, 0), false));

    frame.pack();
    frame.setVisible(true);
  }

  private static class ColorWheelPanel extends JPanel {

    private final ColorWheel myColorWheel;
    private final BrightnessComponent myBrightnessComponent;

    private ColorWheelPanel(Consumer<Color> listener) {
      setLayout(new BorderLayout());
      setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));

      myColorWheel = new ColorWheel();
      add(myColorWheel, BorderLayout.CENTER);

      myColorWheel.addListener(listener);

      myBrightnessComponent = new BrightnessComponent();

      myBrightnessComponent.addListener(new Consumer<Integer>() {
        @Override
        public void consume(Integer value) {
          myColorWheel.setBrightness(1f - (value / 100f));
          myColorWheel.repaint();
        }
      });

      add(myBrightnessComponent, BorderLayout.EAST);
    }

    public void setColor(Color color) {
      float[] hsb = new float[3];
      Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), hsb);

      myBrightnessComponent.setValue(100 - (int)(hsb[2] * 100));
      myBrightnessComponent.repaint();

      myColorWheel.updateHSBValue(hsb[0], hsb[1], hsb[2]);
    }
  }

  private static class BrightnessComponent extends JComponent {
    private static final int OFFSET = 11;
    private int myYValue = 0;
    private int myValue = 0;

    private CopyOnWriteArrayList<Consumer<Integer>> myListeners = new CopyOnWriteArrayList<Consumer<Integer>>();

    private BrightnessComponent() {
      addMouseMotionListener(new MouseAdapter() {
        @Override
        public void mouseDragged(MouseEvent e) {
          processMouse(e);
        }
      });

      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          processMouse(e);
        }
      });

      addMouseWheelListener(new MouseWheelListener() {
        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
          final int amount = e.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL ? e.getUnitsToScroll() * e.getScrollAmount() :
                             e.getWheelRotation() < 0 ? -e.getScrollAmount() : e.getScrollAmount();
          int y = myYValue + amount;
          y = y < OFFSET ? OFFSET : y;
          y = y > (getHeight() - 12) ? getHeight() - 12 : y;

          myYValue = y;
          myValue = yToValue(myYValue);

          repaint();
          fireValueChanged();
        }
      });

      addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          setValue(getValue());
          fireValueChanged();
          repaint();
        }
      });
    }

    private void processMouse(MouseEvent e) {
      int y = e.getY();
      y = y < OFFSET ? OFFSET : y;
      y = y > (getHeight() - 12) ? getHeight() - 12 : y;

      myYValue = y;

      myValue = yToValue(myYValue);

      repaint();
      fireValueChanged();
    }

    public void addListener(Consumer<Integer> listener) {
      myListeners.add(listener);
    }

    private void fireValueChanged() {
      for (Consumer<Integer> listener : myListeners) {
        listener.consume(myValue);
      }
    }

    // 0 - 100
    public void setValue(int value) {
      myYValue = valueToY(value);
      myValue = value;
    }

    public int getValue() {
      return myValue;
    }

    private int yToValue(int y) {
      y -= OFFSET;
      final int height = getHeight();
      float proportion = (height - 23) / 100f;
      return (int)(y / proportion);
    }

    private int valueToY(int value) {
      final int height = getHeight();
      float proportion = (height - 23) / 100f;
      return OFFSET + (int)(value * proportion);
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(22, 100);
    }

    @Override
    public Dimension getMinimumSize() {
      return new Dimension(22, 50);
    }

    @Override
    protected void paintComponent(Graphics g) {
      final Graphics2D g2d = (Graphics2D)g;
      g2d.setPaint(new GradientPaint(0f, 0f, Color.WHITE, 0f, getHeight(), Color.BLACK));
      g.fillRect(7, 10, 12, getHeight() - 20);

      g.setColor(new Color(150, 150, 150));
      g.drawRect(7, 10, 12, getHeight() - 20);

      g.setColor(new Color(250, 250, 250));
      g.drawRect(8, 11, 10, getHeight() - 22);

      drawKnob(g2d, 7, myYValue);
    }

    private static void drawKnob(Graphics2D g2d, int x, int y) {
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      y -= 6;

      Polygon arrowShadow = new Polygon();
      arrowShadow.addPoint(x - 5, y + 1);
      arrowShadow.addPoint(x + 7, y + 7);
      arrowShadow.addPoint(x - 5, y + 13);

      g2d.setColor(new Color(0, 0, 0, 70));
      g2d.fill(arrowShadow);

      Polygon arrowHead = new Polygon();
      arrowHead.addPoint(x - 6, y);
      arrowHead.addPoint(x + 6, y + 6);
      arrowHead.addPoint(x - 6, y + 12);

      g2d.setColor(new Color(153, 51, 0));
      g2d.fill(arrowHead);
    }
  }

  private static class ColorWheel extends JComponent {
    private static final int BORDER_SIZE = 5;
    private float myBrightness = 1f;
    private float myHue = 1f;
    private float mySaturation = 0f;

    private Image myImage;
    private Rectangle myWheel;

    private boolean myShouldInvalidate = true;

    private Color myColor;

    private CopyOnWriteArrayList<Consumer<Color>> myListeners = new CopyOnWriteArrayList<Consumer<Color>>();

    private ColorWheel() {
      setOpaque(true);
      addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          myShouldInvalidate = true;
        }
      });

      addMouseMotionListener(new MouseAdapter() {
        @Override
        public void mouseDragged(MouseEvent e) {
          final int x = e.getX();
          final int y = e.getY();
          int midx = myWheel.x + myWheel.width / 2;
          int midy = myWheel.y + myWheel.height / 2;
          double s, h;
          s = Math.sqrt((double)((x - midx) * (x - midx) + (y - midy) * (y - midy))) / (myWheel.height / 2);
          h = -Math.atan2((double)(y - midy), (double)(x - midx)) / (2 * Math.PI);
          if (h < 0) h += 1.0;
          if (s > 1) s = 1.0;

          setHSBValue((float)h, (float)s, myBrightness);
        }
      });

      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          final int x = e.getX();
          final int y = e.getY();
          int midx = myWheel.x + myWheel.width / 2;
          int midy = myWheel.y + myWheel.height / 2;
          double s, h;
          s = Math.sqrt((double)((x - midx) * (x - midx) + (y - midy) * (y - midy))) / (myWheel.height / 2);
          h = -Math.atan2((double)(y - midy), (double)(x - midx)) / (2 * Math.PI);
          if (h < 0) h += 1.0;
          if (s <= 1) {
            setHSBValue((float)h, (float)s, myBrightness);
          }
        }
      });
    }

    private void setHSBValue(float h, float s, float b) {
      myHue = h;
      mySaturation = s;

      myColor = new Color(Color.HSBtoRGB(h, s, b));

      fireColorChanged();

      repaint();
    }

    public void updateHSBValue(float h, float s, float b) {
      myHue = h;
      mySaturation = s;
      myBrightness = b;
      myImage = null;

      repaint();
    }

    public void addListener(Consumer<Color> listener) {
      myListeners.add(listener);
    }

    private void fireColorChanged() {
      for (Consumer<Color> listener : myListeners) {
        listener.consume(myColor);
      }
    }

    public void setBrightness(float brightness) {
      if (brightness != myBrightness) {
        myBrightness = brightness;
        myImage = null;
        setHSBValue(myHue, mySaturation, brightness);
      }
    }

    @Override
    public Dimension getPreferredSize() {
      return getMinimumSize();
    }

    @Override
    public Dimension getMinimumSize() {
      return new Dimension(300, 300);
    }

    @Override
    protected void paintComponent(Graphics _g) {
      final Dimension size = getSize();
      int _size = Math.min(size.width, size.height);
      _size = Math.min(_size, 600);

      if (myImage != null && myShouldInvalidate) {
        if (myImage.getWidth(null) != _size) {
          myImage = null;
        }
      }

      myShouldInvalidate = false;

      if (myImage == null) {
        myImage = createImage(new ColorWheelImageProducer(_size - BORDER_SIZE * 2, _size - BORDER_SIZE * 2, myBrightness));
        myWheel = new Rectangle(BORDER_SIZE, BORDER_SIZE, _size - BORDER_SIZE * 2, _size - BORDER_SIZE * 2);
      }

      _g.setColor(UIManager.getColor("Panel.background"));
      _g.fillRect(0, 0, getWidth(), getHeight());

      _g.drawImage(myImage, myWheel.x, myWheel.y, null);

      int midx = myWheel.x + myWheel.width / 2;
      int midy = myWheel.y + myWheel.height / 2;
      _g.setColor(Color.white);
      int arcw = (int)(myWheel.width * mySaturation / 2);
      int arch = (int)(myWheel.height * mySaturation / 2);
      double th = myHue * 2 * Math.PI;
      final int x = (int)(midx + arcw * Math.cos(th));
      final int y = (int)(midy - arch * Math.sin(th));
      _g.fillRect(x - 2, y - 2, 4, 4);
      _g.setColor(Color.BLACK);
      _g.drawRect(x - 2, y - 2, 4, 4);
    }
  }

  private static class ColorPreviewComponent extends JComponent {
    private Color myColor;

    private ColorPreviewComponent() {
      setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(100, 32);
    }

    public void setColor(Color c) {
      myColor = c;
      repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
      final Insets i = getInsets();
      final Rectangle r = getBounds();

      final int width = r.width - i.left - i.right;
      final int height = r.height - i.top - i.bottom;

      g.setColor(myColor);
      g.fillRect(i.left, i.top, width, height);

      g.setColor(Color.BLACK);
      g.drawRect(i.left, i.top, width - 1, height - 1);

      g.setColor(Color.WHITE);
      g.drawRect(i.left + 1, i.top + 1, width - 3, height - 3);
    }
  }

  public static class NumberDocument extends PlainDocument {

    private final boolean myHex;

    public NumberDocument() {
      this(false);
    }

    public NumberDocument(boolean hex) {
      myHex = hex;
    }

    public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
      char[] source = str.toCharArray();
      char[] result = new char[source.length];
      int j = 0;

      for (int i = 0; i < result.length; i++) {
        if (myHex ? "0123456789abcdefABCDEF".indexOf(source[i]) >= 0 : Character.isDigit(source[i])) {
          result[j++] = source[i];
        }
        else {
          Toolkit.getDefaultToolkit().beep();
        }
      }
      super.insertString(offs, new String(result, 0, j), a);
    }
  }

  private static class RecentColorsComponent extends JComponent {
    private static final int WIDTH = 10 * 30 + 13;
    private static final int HEIGHT = 62 + 3;

    private List<Color> myRecentColors = new ArrayList<Color>();

    private RecentColorsComponent(final Consumer<Color> listener, boolean restoreColors) {
      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          Color color = getColor(e);
          if (color != null) {
            listener.consume(color);
          }
        }
      });

      if (restoreColors) {
        restoreColors();
      }
    }

    private void restoreColors() {
      final String value = PropertiesComponent.getInstance().getValue(COLOR_CHOOSER_COLORS_KEY);
      if (value != null) {
        final List<String> colors = StringUtil.split(value, ",,,");
        for (String color : colors) {
          myRecentColors.add(new Color(Integer.parseInt(color)));
        }
      }
    }

    @Override
    public String getToolTipText(MouseEvent event) {
      Color color = getColor(event);
      if (color != null) {
        return String.format("R: %d G: %d B: %d", color.getRed(), color.getGreen(), color.getBlue());
      }

      return super.getToolTipText(event);
    }

    @Nullable
    private Color getColor(MouseEvent event) {
      Pair<Integer, Integer> pair = pointToCellCoords(event.getPoint());
      if (pair != null) {
        int ndx = pair.second + pair.first * 10;
        if (myRecentColors.size() > ndx) {
          return myRecentColors.get(ndx);
        }
      }

      return null;
    }

    public void saveColors() {
      final List<String> values = new ArrayList<String>();
      for (Color recentColor : myRecentColors) {
        if (recentColor == null) break;
        values.add(Integer.toString(recentColor.getRGB()));
      }

      PropertiesComponent.getInstance().setValue(COLOR_CHOOSER_COLORS_KEY, StringUtil.join(values, ",,,"));
    }

    public void appendColor(Color c) {
      if (!myRecentColors.contains(c)) {
        myRecentColors.add(c);
      }

      if (myRecentColors.size() > 20) {
        myRecentColors = new ArrayList<Color>(myRecentColors.subList(myRecentColors.size() - 20, myRecentColors.size()));
      }
    }

    private Pair<Integer, Integer> pointToCellCoords(Point p) {
      int x = p.x;
      int y = p.y;

      final Insets i = getInsets();
      final Dimension d = getSize();

      final int left = i.left + (d.width - i.left - i.right - WIDTH) / 2;
      final int top = i.top + (d.height - i.top - i.bottom - HEIGHT) / 2;

      int col = (x - left - 2) / 31;
      col = col > 9 ? 9 : col;
      int row = (y - top - 2) / 31;
      row = row > 1 ? 1 : row;

      return Pair.create(row, col);
    }

    @Override
    public Dimension getPreferredSize() {
      return getMinimumSize();
    }

    @Override
    public Dimension getMinimumSize() {
      return new Dimension(WIDTH, HEIGHT);
    }

    @Override
    protected void paintComponent(Graphics g) {
      final Insets i = getInsets();

      final Dimension d = getSize();

      final int left = i.left + (d.width - i.left - i.right - WIDTH) / 2;
      final int top = i.top + (d.height - i.top - i.bottom - HEIGHT) / 2;

      g.setColor(Color.WHITE);
      g.fillRect(left, top, WIDTH, HEIGHT);

      g.setColor(Color.GRAY);
      g.drawLine(left + 1, i.top + HEIGHT / 2, left + WIDTH - 3, i.top + HEIGHT / 2);
      g.drawRect(left + 1, top + 1, WIDTH - 3, HEIGHT - 3);


      for (int k = 1; k < 10; k++) {
        g.drawLine(left + 1 + k * 31, top + 1, left + 1 + k * 31, top + HEIGHT - 3);
      }

      for (int r = 0; r < myRecentColors.size(); r++) {
        int row = r / 10;
        int col = r % 10;
        Color color = myRecentColors.get(r);
        g.setColor(color);
        g.fillRect(left + 2 + col * 30 + col + 1, top + 2 + row * 30 + row + 1, 28, 28);
      }
    }
  }

  private static class ColorPickerDialog extends DialogWrapper {

    private final Color myPreselectedColor;
    private ColorPicker myColorPicker;

    public ColorPickerDialog(Component parent, String caption, Color preselectedColor) {
      super(parent, false);
      setTitle(caption);
      myPreselectedColor = preselectedColor;
      setResizable(false);
      setOKButtonText("Choose");
      init();
    }

    @Override
    protected JComponent createCenterPanel() {
      if (myColorPicker == null) {
        myColorPicker = new ColorPicker(myPreselectedColor);
      }

      return myColorPicker;
    }

    public Color getColor() {
      return myColorPicker.getColor();
    }

    @Override
    protected void doOKAction() {
      myColorPicker.appendRecentColor();
      myColorPicker.saveRecentColors();

      super.doOKAction();
    }
  }

  /**
   * Produces the image of a ColorWheel.
   *
   * @author Werner Randelshofer
   * @version 1.0 August 27, 2005 Created.
   * @see ColorWheel
   */
  public static class ColorWheelImageProducer extends MemoryImageSource {
    private int[] myPixels;
    private int myWidth, myHeight;
    private float myBrightness = 1f;

    private float[] myHues;
    private float[] mySat;
    private int[] myAlphas;

    public ColorWheelImageProducer(int w, int h, float brightness) {
      super(w, h, null, 0, w);
      myPixels = new int[w * h];
      myWidth = w;
      myHeight = h;
      myBrightness = brightness;
      generateLookupTables();
      newPixels(myPixels, ColorModel.getRGBdefault(), 0, w);
      setAnimated(true);
      generateColorWheel();
    }

    public int getRadius() {
      return Math.min(myWidth, myHeight) / 2 - 2;
    }

    private void generateLookupTables() {
      mySat = new float[myWidth * myHeight];
      myHues = new float[myWidth * myHeight];
      myAlphas = new int[myWidth * myHeight];
      float radius = getRadius();

      // blend is used to create a linear alpha gradient of two extra pixels
      float blend = (radius + 2f) / radius - 1f;

      // Center of the color wheel circle
      int cx = myWidth / 2;
      int cy = myHeight / 2;

      for (int x = 0; x < myWidth; x++) {
        int kx = x - cx; // Kartesian coordinates of x
        int squarekx = kx * kx; // Square of kartesian x

        for (int y = 0; y < myHeight; y++) {
          int ky = cy - y; // Kartesian coordinates of y

          int index = x + y * myWidth;
          mySat[index] = (float)Math.sqrt(squarekx + ky
                                                           * ky)
                               / radius;
          if (mySat[index] <= 1f) {
            myAlphas[index] = 0xff000000;
          }
          else {
            myAlphas[index] = (int)((blend - Math.min(blend,
                                                    mySat[index] - 1f)) * 255 / blend) << 24;
            mySat[index] = 1f;
          }
          if (myAlphas[index] != 0) {
            myHues[index] = (float)(Math.atan2(ky, kx) / Math.PI / 2d);
          }
        }
      }
    }

    public void generateColorWheel() {
      for (int index = 0; index < myPixels.length; index++) {
        if (myAlphas[index] != 0) {
          myPixels[index] = myAlphas[index]
                          | 0xffffff
                            & Color.HSBtoRGB(myHues[index],
                                             mySat[index], myBrightness);
        }
      }
      newPixels();
    }
  }
}
