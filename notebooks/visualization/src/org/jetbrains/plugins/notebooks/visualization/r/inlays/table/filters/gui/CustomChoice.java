/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.gui;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.Serializable;
import java.text.Format;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Class to specify a custom filter in the choices list.<br>
 *
 * <p>A custom choice allows to specify custom filters that can be selected as a
 * choice in the filter editor. A custom choice is associated always to a text,
 * which the user can input to select the given choice.<br>
 * </p>
 *
 * <p>It is also possible to specify how the custom filter is rendered; by
 * default it is displayed the icon -if provided-, and the text, unless the user
 * provides a custom renderer.<br>
 * </p>
 *
 * <p>The order of the custom choices on the choices list can be modified with
 * the precedence attribute. By default, custom choices are sorted by their
 * textual representation. If a precedence is given, lower values are displayed
 * first.</p>
 */
public abstract class CustomChoice implements Serializable {

  private static final long serialVersionUID = -2894134608058210332L;

  public final static int DEFAULT_PRECEDENCE = 0;
  public final static int MATCH_ALL_PRECEDENCE = -255;

  static final RowFilter passAllRawFilter = new RowFilter() {
    @Override
    public boolean include(Entry entry) {
      return true;
    }
  };

  /**
   * Empty filter, returns all entries.
   */
  public final static CustomChoice MATCH_ALL = new CustomChoice("", null,
                                                                MATCH_ALL_PRECEDENCE) {

    private static final long serialVersionUID = -8964761397870138666L;

    @Override
    public RowFilter getFilter(IFilterEditor editor) {
      return passAllRawFilter;
    }
  };

  /**
   * Special empty filter, returns all entries with null or empty values.
   */
  public final static CustomChoice MATCH_EMPTY = new CustomChoice(
    FilterSettings.matchEmptyFilterString,
    FilterSettings.matchEmptyFilterIcon) {

    private static final long serialVersionUID = 5654162608079137456L;

    @Override
    public RowFilter getFilter(final IFilterEditor editor) {
      final int modelIndex = editor.getModelIndex();

      return new RowFilter() {
        @Override
        public boolean include(Entry entry) {
          Object o = entry.getValue(modelIndex);
          if (o == null) {
            return true;
          }

          if (editor.getRenderer() != null) {
            return false;
          }

          Format fmt = editor.getFormat();
          String s = (fmt == null) ? o.toString() : fmt.format(o);
          return (s == null) || (s.trim().length() == 0);
        }
      };
    }
  };

  /**
   * Creates a set of CustomChoice instances, one for each provided choice.
   * The representation for each choice is the stringfied representation of
   * the passed instance.
   */
  public static Set<CustomChoice> createSet(Object[] choices) {
    Set<CustomChoice> ret = new HashSet<>();
    for (Object o : choices) {
      ret.add(create(o, o.toString()));
    }

    return ret;
  }

  /**
   * Creates a set of CustomChoice instances, one for each provided choice.
   * The representation for each choice is the stringfied representation of
   * the passed instance.
   */
  public static Set<CustomChoice> createSet(Collection choices) {
    Set<CustomChoice> ret = new HashSet<>();
    for (Object o : choices) {
      ret.add(create(o, o.toString()));
    }

    return ret;
  }

  /**
   * Creates a CustomChoice that matches the given object; its stringfied
   * format is the representation shown to the user.
   */
  public static CustomChoice create(Object choice) {
    return create(choice, choice.toString());
  }

  /**
   * Creates a CustomChoice that matches the given object, with the provided
   * representation.<br>
   * The choice can be a {@link Pattern} instance, in which case it is
   * performed a complete regular expression match.
   */
  public static CustomChoice create(final Object choice, String repr) {
    if (choice instanceof Pattern) {
      return new CustomChoice(repr) {

        private static final long serialVersionUID = -3239105477862513930L;

        @Override
        public RowFilter getFilter(final IFilterEditor ed) {
          final int index = ed.getModelIndex();
          final Pattern pattern = (Pattern)choice;
          return new RowFilter() {
            @Override
            public boolean include(Entry entry) {
              Object o = entry.getValue(index);
              if (o == null) {
                return false;
              }
              Format fmt = ed.getFormat();
              String s = (fmt == null) ? o.toString() :
                         fmt.format(o);
              return pattern.matcher(s).matches();
            }
          };
        }
      };
    }
    return new CustomChoice(repr) {
      private static final long serialVersionUID = -3573642873044716998L;

      @Override
      public RowFilter getFilter(final IFilterEditor editor) {
        final int index = editor.getModelIndex();
        final String string = (choice instanceof String)
                              ? (String)choice : null;
        return new RowFilter() {
          @Override
          public boolean include(Entry entry) {
            Object o = entry.getValue(index);
            if ((string != null) && (o instanceof String)
                && editor.isIgnoreCase()) {
              return string.equalsIgnoreCase((String)o);
            }

            return choice.equals(o);
          }
        };
      }
    };
  }

  private Icon icon;
  private String str;
  private int precedence;

  /**
   * Full constructor.
   */
  public CustomChoice(String representation, Icon icon, int precedence) {
    this.icon = icon;
    this.str = representation;
    this.precedence = precedence;
  }

  /**
   * Creates a custom choice without associated icon, and with default
   * precedence, to be handled exclusively as text.
   */
  public CustomChoice(String representation) {
    this(representation, null, DEFAULT_PRECEDENCE);
  }

  /**
   * Creates a custom choice with associated icon and default precedence.
   */
  public CustomChoice(String representation, Icon icon) {
    this(representation, icon, DEFAULT_PRECEDENCE);
  }

  /**
   * Returns the background color, or null to use the default one.
   *
   * @param editor     the editor where the choice is used
   * @param isSelected true if the choice is selected
   * @return null to use the default one
   */
  public Color getBackground(IFilterEditor editor, boolean isSelected) {
    return null;
  }

  /**
   * Returns the foreground color, or null to use the default one.
   *
   * @param editor     the editor where the choice is used
   * @param isSelected true if the choice is selected
   * @return null to use the default one
   */
  public Color getForeground(IFilterEditor editor, boolean isSelected) {
    return null;
  }

  /**
   * Returns the font, or null to use the default one.
   *
   * @param editor     the editor where the choice is used
   * @param isSelected true if the choice is selected
   * @return null to use the default one
   */
  public Font getFont(IFilterEditor editor, boolean isSelected) {
    return null;
  }

  /**
   * Returns the associated icon, if any.
   */
  public Icon getIcon() {
    return icon;
  }

  /**
   * Sets the associated icon.
   */
  public void setIcon(Icon icon) {
    this.icon = icon;
  }

  /**
   * Decorates the choice on the given editor.
   *
   * @param editor     the editor where the choice is used
   * @param isSelected true if the choice is selected
   * @param c          the component to decorate
   * @param g          the decoration context
   */
  public void decorateComponent(IFilterEditor editor,
                                boolean isSelected,
                                JComponent c,
                                Graphics g) {
    if (icon != null) {
      Icon use;
      if (c.isEnabled()) {
        use = icon;
      }
      else {
        use = UIManager.getLookAndFeel().getDisabledIcon(c, icon);
      }

      FontMetrics metrics = g.getFontMetrics();

      //   FontMetrics metrics = g.getFontMetrics(editor.getLook().getCustomChoiceDecorator().getFont(this, editor, isSelected));
      int x = Math.max(4 + metrics.stringWidth(toString()),
                       (c.getWidth() - use.getIconWidth()) / 2);
      int y = (c.getHeight() - use.getIconHeight()) / 2;
      use.paintIcon(c, g, x, y);
    }
  }

  /**
   * Returns the precedence value.
   */
  public int getPrecedence() {
    return precedence;
  }

  /**
   * Sets the precedence value. Choices with lower precedence are displayed
   * first.
   */
  public void setPrecedence(int precedence) {
    this.precedence = precedence;
  }

  /**
   * Returns the associated string.
   */
  public String getRepresentation() {
    return str;
  }

  /**
   * Sets the representation value.
   */
  public void setRepresentation(String representation) {
    this.str = representation;
  }

  /**
   * Returns the associated filter.
   */
  public abstract RowFilter getFilter(IFilterEditor editor);

  /**
   * Returns the string representation of the filter.
   */
  @Override
  final public String toString() {
    return str;
  }

  static private final @NotNull NotNullFunction<String, String> escaper = StringUtil.escaper(true, "\"");
}
