/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.gui.editor;

import org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.gui.CustomChoice;

import javax.swing.*;
import java.text.Format;
import java.util.*;

/**
 * List model to handle the choices in the popup menu: it assumes that all the
 * content belongs to the class of the associated column, which is sorted using
 * a provided comparator (it is okay if no all elements have the same class as
 * far as the comparator is able to cope with it).<br>
 *
 * <p>It distinguishes between rendered content -where there is no need to sort
 * or search alphabetically-, and content handled as strings (every non rendered
 * content is handled as a string).<br>
 * </p>
 *
 * <p>It provides, in special, functionality to keep the content sorted, and to
 * perform text search on the content.</p>
 */
public class ChoicesListModel extends AbstractListModel
    implements Comparator<ChoicesListModel.Choice> {

    private static final long serialVersionUID = -8795357002432721893L;
    private Format format;
    private Comparator contentComparator;
    private Comparator<String> strComparator;
    private boolean renderedContent;
    private final TreeSet<Choice> content;
    private TreeSet<Choice> alphaSortedContent;
    private Object[] flatContent;
    private int size;

    public ChoicesListModel(Format     format,
                            Comparator choicesComparator,
                            Comparator<String> stringComparator) {
        this.format = format;
        this.strComparator = stringComparator;
        this.contentComparator = choicesComparator;
        this.content = new TreeSet<>(this);
        clearContent();
    }

    /**
     * Specifies that the content requires no conversion to strings.
     *
     * @return  true if this is a change
     */
    public boolean setRenderedContent(Comparator choicesComparator,
                                      Comparator<String> stringComparator) {
        if (updateComparators(choicesComparator, stringComparator)
                || !renderedContent) {
            renderedContent = true;
            this.strComparator = stringComparator;
            clearContent();
            return true;
        }

        return false;
    }

    /**
     * Specifies that the content is to be handled as strings.
     *
     * @param format the formatter to convert objects to strings, can be null
     * @param choicesComparator the comparator used to sort choices on the
     *   list model. Can be null to sort content alphabetically.
     * @param stringComparator
     * @return  true if choices should be added again.
     */
    public boolean setStringContent(Format             format,
                                    Comparator         choicesComparator,
                                    Comparator<String> stringComparator) {
    	if (stringComparator.equals(choicesComparator)) {
    		choicesComparator = null;
    	}
        boolean change = updateComparators(choicesComparator, stringComparator)
                || renderedContent;
        if (!change && (format != this.format)) {
            change = (this.format == null) || (format == null)
                    || !this.format.equals(format);
        }

        if (change) {
            this.format = format;
            this.strComparator = stringComparator;
            renderedContent = false;
            clearContent();
        }

        return change;
    }

    @Override public int getSize() {
        return size;
    }

    @Override public Object getElementAt(int index) {
        return flatContent()[index];
    }

    /** Clears all content (but ALL matcher). */
    public void clearContent() {
        int currentSize = size;
        content.clear();
        content.add(new Choice(CustomChoice.MATCH_ALL, null));
        fireIntervalRemoved(this, size = 1, currentSize);
    }

    /**
     * Adds additional choices.<br>
     * If the content is text-based, the choices are converted into Strings, and
     * sorted; additionally, choices are also escaped.<br>
     * Otherwise, no sorting is performed, although duplicates are still
     * discarded
     *
     * @return  true if there are any changes after the operation
     */
    public boolean addContent(Collection addedContent, IChoicesParser parser) {
        boolean changed = false;
        for (Object o : addedContent) {
            String s = null;
            if (o == null) {
                o = CustomChoice.MATCH_EMPTY;
            } else if (!renderedContent && !(o instanceof CustomChoice)) {
                // if null, content is rendered, no need to handle strings
                s = (format == null) ? o.toString() : format.format(o);
                if (s.length() == 0) {
                    o = CustomChoice.MATCH_EMPTY;
                } else {
                    s = parser.escapeChoice(s);
                }
            }

            changed = content.add(new Choice(o, s)) || changed;
        }

        if (changed) {
            flatContent = null;
            alphaSortedContent = null;
            fireContentsChanged(this, 0, size = content.size());
        }

        return changed;
    }

    /** @see  PopupComponent#selectBestMatch(Object) */
    public ChoiceMatch getBestMatch(Object hint) {
        String str = null;
        if (!renderedContent && (hint instanceof String)) {
            // is a string (what the user enters), but if there is a format, it
            // can correspond to an existing element. For example, the right
            // format being for a date "07/05/12", but the user enters
            // "7/5/12". In this case, we should automatically return the
            // updated string. For the time being, this conversion is not yet
            // made (requires changes on the EditorComponent, and side effects
            // must be checked...)
            str = (String) hint;
            hint = null; // for the time being, we
        }
        Choice choice = new Choice(hint, str);
        ChoiceMatch ret = new ChoiceMatch();
        Choice match = (hint == null) ? null : content.floor(choice);
        if ((match != null) && match.equals(choice)) {
            flatContent(); // ensure that the positions are calculated
            ret.content = match.get(renderedContent);
            ret.index = match.idx;
            ret.exact = true;
        } else if (!renderedContent) {
            TreeSet<Choice> alphaContent = getAlphabeticallySortedContent();
            Choice top = alphaContent.ceiling(choice);
            Choice low = alphaContent.floor(choice);
            int len = choice.str.length();
            int clen = (top == null)
                ? -1
                : ChoiceMatch.getMatchingLength(top.str, choice.str,
                    strComparator);
            int flen = (low == null)
                ? -1
                : ChoiceMatch.getMatchingLength(low.str, choice.str,
                    strComparator);
            match = (clen > flen) ? top : low;
            ret.index = match.idx;
            ret.content = match.get(renderedContent);
            ret.len = Math.max(clen, flen);
            ret.exact = (match.str.length() == ret.len) && (len == 0 || ret.len > 0);
        }
        return ret;
    }


    /**
     * Returns the text that could complete the given string<br>
     * The completion string is the larger string that matches all existing
     * options that already match the provided base.
     *
     * @param  unsortedList:  additional content to comb through
     */
    public String getCompletion(String base, List<?> unsortedList) {
        int minLen = base.length();
        int maxLen = Integer.MAX_VALUE;
        String ret = null;
        Iterator<Choice> it = getAlphabeticallySortedContent().tailSet(
                new Choice(base, base), true)
                .iterator();
        Iterator<?> its = unsortedList.iterator();
        while ((maxLen > minLen) && (it.hasNext() || its.hasNext())) {
            String s = it.hasNext() ? it.next().str : its.next().toString();
            int match = ChoiceMatch.getMatchingLength(base, s, strComparator);
            if (match == minLen) {
                if (ret == null) {
                    ret = s;
                    maxLen = s.length();
                } else {
                    maxLen = Math.min(maxLen,
                            ChoiceMatch.getMatchingLength(ret, s,
                                strComparator));
                }
            }
        }

        return (ret == null) ? "" : ret.substring(minLen, maxLen);
    }

    private TreeSet<Choice> getAlphabeticallySortedContent() {
        if (alphaSortedContent == null) {
            flatContent(); // ensure we have the positions on the Choices
            alphaSortedContent = new TreeSet<>(new ChoiceTextComparator(strComparator));
            alphaSortedContent.addAll(content);
        }

        return alphaSortedContent;
    }

    private Object[] flatContent() {
        if (flatContent == null) {
            int i = size;
            flatContent = new Object[size];

            Iterator<Choice> it = content.descendingIterator();
            while (i-- > 0) {
                flatContent[i] = it.next().index(i).get(renderedContent);
            }
        }

        return flatContent;
    }

    private boolean updateComparators(Comparator choicesComparator,
                                      Comparator<String> stringComparator) {
        boolean same = this.strComparator.equals(stringComparator);
        if (same){
        	if (choicesComparator==null){
        		same = this.contentComparator==null;
        	} else {
        		same = choicesComparator.equals(this.contentComparator);
        	}
        }
        this.contentComparator = choicesComparator;
        this.strComparator = stringComparator;
        return !same;
    }

    public Comparator<String> getStringComparator() {
        return strComparator;
    }

    @Override public int compare(Choice w1, Choice w2) {
        Object o1 = w1.o;
        Object o2 = w2.o;
        if (o1 instanceof CustomChoice) {
            if (o2 instanceof CustomChoice) {
                CustomChoice c1 = (CustomChoice) o1;
                CustomChoice c2 = (CustomChoice) o2;
                int ret = c1.getPrecedence() - c2.getPrecedence();
                if (ret == 0) {
                    // in this case, the comparator is string comparator
                    ret = strComparator.compare(w1.str, w2.str);
                }

                return ret;
            }
            return -1;
        }
        if (o2 instanceof CustomChoice) {
            return 1;
        }
        int diff=0;
        if (contentComparator!=null){
        	diff = contentComparator.compare(o1, o2);
        	if (renderedContent || diff==0){
        		return diff;
        	}
        }
        int sdiff = strComparator.compare(w1.str, w2.str);
        return sdiff==0 || diff==0? sdiff : diff;
    }

    /**
     * Instances in the model are wrapped as Choice objects, with added
     * information on the stringfied representation of the object -null if the
     * content is rendered- and the position on the model when is sorted
     * alphabetically.
     */
    static public class Choice {
        public Object o;
        public String str;
        public int idx;

        public Choice(Object content, String repr) {
            this.o = content;
            if (content instanceof CustomChoice) {
                // choice comparator uses the string for comparison
                this.str = ((CustomChoice) content).getRepresentation();
            } else {
                this.str = repr;
            }
        }

        public Choice index(int position) {
            this.idx = position;
            return this;
        }

        public Object get(boolean rendered) {
            return (rendered || (o instanceof CustomChoice)) ? o : str;
        }

        @Override public int hashCode() {
            return o.hashCode();
        }

        @Override public boolean equals(Object choice) {
            return (choice instanceof Choice) && ((Choice) choice).o.equals(o);
        }
    }

    /** Comparator to compare Wrappers by their string member. */
    static private final class ChoiceTextComparator implements Comparator<Choice> {

        private final Comparator<String> stringComparator;

        private ChoiceTextComparator(Comparator<String> stringComparator) {
            this.stringComparator = stringComparator;
        }

        @Override public int compare(Choice w1, Choice w2) {
            return stringComparator.compare(w1.str, w2.str);
        }
    }

}
