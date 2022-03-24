/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.gui;

import org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.IParser;
import org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.parser.DateComparator;
import org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.parser.Parser;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.text.*;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Default {@link Format} instances, supporting all the basic java types<br>
 * It also includes support for {@link Comparator} of {@link Date} instances.
 * <br>
 * The default {@link IParser} is automatically configured to use these {@link
 * Format} instances, when created by the {@link TableFilterHeader}.<br>
 * Users can add any {@link Format} or {@link Comparator} definitions, as the
 * class is used as a singleton.
 */
public class ParserModel implements IParserModel {

    /** Format for primitive types (plus Date). */
    private static final Map<Class<?>, Format> basicFormats = new HashMap<>();

    /** String comparator, case dependent. */
    private static Comparator<String> strComparator;

    /** String comparator, ignoring case. */
    private static Comparator<String> icStrComparator;

    /** Formats defined for the model. */
    private final Map<Class<?>, Format> formats = new HashMap<>();

    /** Comparators defined explicitly for the model. */
    private final Map<Class<?>, Comparator> comparators = new HashMap<>();

    /** Ignore case flag. */
    private boolean ignoreCase;

    /** Helper to handle property change events. */
    private final PropertyChangeSupport propertiesHandler = new PropertyChangeSupport(this);


    public ParserModel() {
        // ensure proper behavior concerning ignoring case/string comparator
        // and handle the format and comparator for Dates
        ignoreCase = !FilterSettings.ignoreCase;
        setIgnoreCase(!ignoreCase);
        setFormat(Date.class, getBasicFormat(Date.class));
    }

    @Override public void addPropertyChangeListener(
            PropertyChangeListener listener) {
        propertiesHandler.addPropertyChangeListener(listener);
    }

    @Override public void removePropertyChangeListener(
            PropertyChangeListener listener) {
        propertiesHandler.removePropertyChangeListener(listener);
    }

    @Override public IParser createParser(IFilterEditor editor) {

        // For Strings, the parser is built with the string format, and no
        // comparator The same applies if the editor has no Format defined and
        // its type is not a primitive Otherwise, it is used the editor's format
        // (or the primitive format), and the editor's comparator, which is
        // never null
        boolean ignoreCase = editor.isIgnoreCase();
        Class<?> cl = editor.getModelClass();
        Format fmt = (cl == String.class) ? null : editor.getFormat();
        Comparator cmp = (fmt == null) ? null : editor.getComparator();

        return createParser(fmt, cmp, getStringComparator(ignoreCase), ignoreCase,
                editor.getModelIndex());
    }

    /** Creates the parser as required with the given parameters */
    protected IParser createParser(Format fmt, Comparator cmp,
                                   Comparator stringCmp, boolean ignoreCase,
                                   int modelIndex) {
        return new Parser(fmt, cmp, stringCmp, ignoreCase, modelIndex);
    }

    @Override public boolean isIgnoreCase() {
        return ignoreCase;
    }

    @Override public void setIgnoreCase(boolean set) {
        if (set != this.ignoreCase) {
            this.ignoreCase = set;
            propertiesHandler.firePropertyChange(IGNORE_CASE_PROPERTY, !set,
                set);
            setComparator(String.class, getStringComparator(set));
        }
    }

    /** Returns the {@link Format} for the given class. */
    @Override public final Format getFormat(Class cl) {
        Format ret = formats.get(cl);
        if (ret == null) {
            if (cl.isEnum()) {
                ret = new EnumTypeFormat(cl);
                formats.put(cl, ret);
            } else {
                ret = getBasicFormat(cl);
            }
        }

        return ret;
    }

    /** Defines the {@link Format} for the given class. */
    @Override public final void setFormat(Class<?> cl, Format fmt) {
        Format old = formats.put(cl, fmt);
        if (old != fmt) {
            propertiesHandler.firePropertyChange(FORMAT_PROPERTY, null, cl);
            // for Dates, there is added logic to deduce the associated
            // comparator
            if (Date.class.isAssignableFrom(cl) && (fmt != null)) {
                Comparator cmp = getComparator(cl);
                if ((cmp == null) || (cmp instanceof DateComparator)) {
                    setComparator(cl, DateComparator.getDateComparator(fmt));
                }
            }
        }
    }

    /** Returns the {@link Comparator} for the given class. */
    @Override public Comparator getComparator(Class<?> cl) {
        Comparator ret = comparators.get(cl);
        if (ret == null) {
            if (cl == String.class) {
                ret = getStringComparator(ignoreCase);
            } else if (Comparable.class.isAssignableFrom(cl)) {
                ret = Comparator.naturalOrder();
            } else {
                ret = DEFAULT_COMPARATOR;
            }
        }

        return ret;
    }

    /** Defines the {@link Comparator} for the given class. */
    @Override public void setComparator(Class<?> cl, Comparator cmp) {
        if (cl == String.class) {
            // do not allow a null comparator for Strings.
            // in addition, retrieve the proper case flag from the comparator
            if (cmp == null) {
                cmp = getStringComparator(ignoreCase);
            } else {
                setIgnoreCase(cmp.compare("a", "A") == 0);
            }
        } else if (cmp == null) {
            cmp = getComparator(cl);
        }

        if (cmp != comparators.put(cl, cmp)) {
            propertiesHandler.firePropertyChange(COMPARATOR_PROPERTY, null, cl);
        }
    }

    @Override public Comparator<String> getStringComparator(boolean noCase) {
        return stringComparator(noCase);
    }

    /** Returns a default singleton comparator for the given case flag. */
    public static Comparator<String> stringComparator(boolean ignoreCase) {
        if (ignoreCase) {
            if (icStrComparator == null) {
                icStrComparator = String.CASE_INSENSITIVE_ORDER;
            }

            return icStrComparator;
        }

        if (strComparator == null) {
            strComparator = Comparator.naturalOrder();
        }

        return strComparator;
    }

    /** Returns the {@link Format} defined for every FilterModel. */
    private static Format getBasicFormat(Class<?> cl) {
        // for Strings, we just use null.
        Format fmt = basicFormats.get(cl);
        if (fmt == null) {
            if (cl == String.class) {
                fmt = new StringTypeFormat();
            } else if (cl == Boolean.class) {
                fmt = new BooleanTypeFormat();
            } else if (cl == Integer.class) {
                fmt = new IntegerTypeFormat();
            } else if (cl == Long.class) {
                fmt = new LongTypeFormat();
            } else if (cl == Short.class) {
                fmt = new ShortTypeFormat();
            } else if (cl == Float.class) {
                fmt = new FloatTypeFormat();
            } else if (cl == Double.class) {
                fmt = new DoubleTypeFormat();
            } else if (cl == Byte.class) {
                fmt = new ByteTypeFormat();
            } else if (cl == Character.class) {
                fmt = new CharacterTypeFormat();
            } else if (cl == Date.class) {
                fmt = getDefaultDateFormat();
            }

            if (fmt != null) {
                basicFormats.put(cl, fmt);
            }
        }

        return fmt;
    }

    private static DateFormat getDefaultDateFormat() {
        String definition = FilterSettings.dateFormat;
        if (definition != null) {
            try {
                return new SimpleDateFormat(definition);
            } catch (Exception ex) { // return the basic format
            }
        }

        return DateFormat.getDateInstance(DateFormat.SHORT);
    }

    static abstract class TypeFormat extends Format {
        private static final long serialVersionUID = -6161901343218446716L;

        @Override public StringBuffer format(Object        obj,
                                             StringBuffer  toAppendTo,
                                             FieldPosition pos) {
            if (obj != null) {
                toAppendTo.append(obj);
            }

            return toAppendTo;
        }

        @Override public abstract Object parseObject(String source)
                                              throws ParseException;

        @Override public Object parseObject(String source, ParsePosition pos) {
            return null;
        }
    }

    /** Factory to build string objects. */
    public static class StringTypeFormat extends TypeFormat {
        private static final long serialVersionUID = 1641138429288273113L;

        @Override public Object parseObject(String source) {
            return source;
        }
    }

    /** Factory to build boolean objects. */
    public static class BooleanTypeFormat extends TypeFormat {
        private static final long serialVersionUID = -6014041038273288651L;

        @Override public Object parseObject(String text) {
            return Boolean.valueOf(text);
        }
    }

    /** Factory to build integer objects. */
    public static class IntegerTypeFormat extends TypeFormat {
        private static final long serialVersionUID = 314115124294512565L;

        @Override public Object parseObject(String text) throws ParseException {
            try {
                return Integer.valueOf(text);
            } catch (NumberFormatException nfe) {
                throw new ParseException(text, 0);
            }
        }
    }

    /** Factory to build long objects. */
    public static class LongTypeFormat extends TypeFormat {
        private static final long serialVersionUID = 1165105738539025608L;

        @Override public Object parseObject(String text) throws ParseException {
            try {
                return Long.valueOf(text);
            } catch (NumberFormatException nfe) {
                throw new ParseException(text, 0);
            }
        }
    }

    /** Factory to build short objects. */
    public static class ShortTypeFormat extends TypeFormat {
        private static final long serialVersionUID = -2237230150685513628L;

        @Override public Object parseObject(String text) throws ParseException {
            try {
                return Short.valueOf(text);
            } catch (NumberFormatException nfe) {
                throw new ParseException(text, 0);
            }
        }
    }

    /** Factory to build float objects. */
    public static class FloatTypeFormat extends TypeFormat {
        private static final long serialVersionUID = 945229095107692481L;

        @Override public Object parseObject(String text) throws ParseException {
            try {
                return Float.valueOf(text);
            } catch (NumberFormatException nfe) {
                throw new ParseException(text, 0);
            }
        }
    }

    /** Factory to build double objects. */
    public static class DoubleTypeFormat extends TypeFormat {
        private static final long serialVersionUID = -6081024614795175063L;

        @Override public Object parseObject(String text) throws ParseException {
            try {
                return Double.valueOf(text);
            } catch (NumberFormatException nfe) {
                throw new ParseException(text, 0);
            }
        }
    }

    /** Factory to build byte objects. */
    public static class ByteTypeFormat extends TypeFormat {
        private static final long serialVersionUID = -8872549512274058519L;

        @Override public Object parseObject(String text) throws ParseException {
            try {
                return Byte.valueOf(text);
            } catch (NumberFormatException nfe) {
                throw new ParseException(text, 0);
            }
        }
    }

    /** Factory to build character objects. */
    public static class CharacterTypeFormat extends TypeFormat {
        private static final long serialVersionUID = -7238741018044298862L;

        @Override public Object parseObject(String text) throws ParseException {
            if (text.length() != 1) {
                throw new ParseException(text, 0);
            }

            return new Character(text.charAt(0));
        }
    }

    /** Factory to build character objects. */
    public static class EnumTypeFormat extends TypeFormat {
        private static final long serialVersionUID = -7238741018044298862L;

        private Class<? extends Enum> enumClass;

        public EnumTypeFormat(Class<? extends Enum> enumClass) {
            this.enumClass = enumClass;
        }

        @Override public Object parseObject(String text) throws ParseException {
            try {
                return Enum.valueOf(enumClass, text);
            } catch (Exception ex) {
                throw new ParseException(text, 0);
            }
        }
    }

    private static final Comparator DEFAULT_COMPARATOR = (o1, o2) -> {
        // on a JTable, sorting will use the string representation, but here
        // is not enough to distinguish on string representation, as it is
        // only used for cases where the content is not converted to String
        int ret = o1.toString().compareTo(o2.toString());
        if ((ret == 0) && !o1.equals(o2)) {
            ret = o1.hashCode() - o2.hashCode();
            if (ret == 0) {
                ret = System.identityHashCode(o1)
                        - System.identityHashCode(o2);
            }
        }

        return ret;
    };

}
