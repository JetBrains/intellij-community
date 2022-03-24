/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.gui;

import org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.IFilter;

import javax.swing.table.TableModel;
import java.text.Format;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Public interface of the editors associated to each table's column. */
public interface IFilterEditor {

    /** Returns the model position associated to this editor. */
    int getModelIndex();

    /** Returns the class associated to the editor on the model. */
    Class<?> getModelClass();

    /**
     * Returns the {@link IFilter} associated to the editor's content<br>
     * The returned instance can then be used to enable or disable the filter
     * and its GUI component.
     */
    IFilter getFilter();

    /**
     * Resets the filter, which implies set its content to empty and reset its
     * history choices.
     */
    void resetFilter();

    /** Sets the content, adapted to the editors' type. */
    void setContent(Object content);

    /** Returns the current editor's content. */
    Object getContent();

    /**
     * Using autoChoices, the choices displayed on the popup menu are
     * automatically extracted from the associated {@link TableModel}.<br>
     * For editors associated to boolean or short enumerations, if
     * AutoCompletion is not set, setting the AutoChoices automatically changes
     * the editable flag to true, unless AutoChoices has the DISABLED value
     */
    void setAutoChoices(AutoChoices mode);

    /** Returns the autoChoices mode. */
    AutoChoices getAutoChoices();

    /** Sets the available choices, shown on the popup menu. */
    void setCustomChoices(Set<CustomChoice> choices);

    /** Returns the current choices. */
    Set<CustomChoice> getCustomChoices();

    /** 
     * Enables or disables the user's interaction; if disabled, the control
     * is disabled but the associated filter remains in place. 
     */
    void setUserInteractionEnabled(boolean enable);
    
    /** Returns the user interaction mode. */
    boolean isUserInteractionEnabled();

    /**
     * Defines the editor, if text based -i.e., without associated {@link
     * ChoiceRenderer}, as editable: this flag means that the user can enter any
     * text, not being limited to the existing choices
     */
    void setEditable(boolean enable);

    /** Returns the editable flag. */
    boolean isEditable();

    /** Sets the ignore case flag. */
    void setIgnoreCase(boolean set);

    /** Returns the ignore case flag. */
    boolean isIgnoreCase();

    /**
     * Sets the {@link Format} required by the editor to handle the user's input
     * when the associated class is not a String<br>
     * It is initially retrieved from the {@link IParserModel}.
     */
    void setFormat(Format format);

    /** Returns the associated {@link Format}. */
    Format getFormat();

    /**
     * Sets the {@link Comparator} required to compare (and sort) instances of
     * the associated class in the table model.<br>
     * This operation sets also this operator as the choices comparator 
     * (see {@link #setChoicesComparator(Comparator)})
     * @param comparator cannot be null
     */
    void setComparator(Comparator comparator);

    /** Returns the associated {@link Comparator}, which is never null. */
    Comparator getComparator();

    /**
     * Sets the {@link Comparator} used to sort out the choices. By default.
     * this is the same operator associated to the editor. Note that editors 
     * associated to enumeration types are sorted by default alphabetically.<br>
     * @param comparator can be set to null to use alphabetic sorting
     * @see IFilterEditor#setComparator(Comparator)
     */
    void setChoicesComparator(Comparator comparator);

    /** Returns the associated {@link Comparator} choices comparator. */
    Comparator getChoicesComparator();
    
    /** Sets the auto completion flag. */
    void setAutoCompletion(boolean enable);

    /** Returns the auto completion flag. */
    boolean isAutoCompletion();

    /** Sets the instant filtering flag. */
    void setInstantFiltering(boolean enable);

    /** Returns the instant filtering flag. */
    boolean isInstantFiltering();

    /** Sets the allow instant vanishing flag. */
    void setAllowedInstantVanishing(boolean enable);

    /** Returns the instant filtering flag. */
    boolean isAllowedInstantVanishing();

    /**
     * Limits the history size.<br>
     * This limit is only used when the popup contains also choices. Otherwise,
     * the maximum history size is to the maximum number of visible rows<br>
     * The max history cannot be greater than the max visible rows
     */
    void setMaxHistory(int size);

    /**
     * Returns the maximum history size, as defined by the user.<br>
     * This is not the real maximum history size, as it depends on the max
     * number of visible rows and whether the popup contains only history or
     * also choices
     */
    int getMaxHistory();

    /**
     * Sets the history contents.
     * @since 4.3.1.0
     */
    void setHistory(List<Object> history);

    /**
     * Returns the current history contents
     * @since 4.3.1.0
     */
    List<Object> getHistory();

    /**
     * Sets the {@link ChoiceRenderer} for the choices / history.
     *
     * <p>It also affects to how the content is rendered<br>
     * If not null, the content cannot be text-edited anymore</p>
     *
     * @param  renderer
     */
    void setRenderer(ChoiceRenderer renderer);

    /** Returns the associated {@link ChoiceRenderer}. */
    ChoiceRenderer getRenderer();

    ///** Returns the current editor's look. */
    //Look getLook();

}
