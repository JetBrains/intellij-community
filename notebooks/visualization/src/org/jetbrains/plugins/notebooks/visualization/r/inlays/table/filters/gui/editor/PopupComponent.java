/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.gui.editor;

import org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.gui.ChoiceRenderer;
import org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.gui.IFilterEditor;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.Format;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Internal editor component, responsible to handle the popup menu, which
 * contains the history and the choices list.
 */
abstract class PopupComponent implements PopupMenuListener {

    private FilterListCellRenderer listRenderer;
    private JScrollPane choicesScrollPane;
    private JScrollPane historyScrollPane;
    private JSeparator separator;
    private EditorBoundsWatcher editorBoundsWatcher = new EditorBoundsWatcher();

    private ChoicesListModel choicesModel;
    private HistoryListModel historyModel;
    JPopupMenu popup;

    /**
     * cancelReason contains the source of the event that cancelled last time
     * the popup menu.
     */
    private Object cancelReason;

    /** This is the total max number of visible rows (history PLUS choices). */
    private int maxVisibleRows;

    /** focusedList always refer to one of choicesList or historyList. */
    JList focusedList;
    JList choicesList;
    JList historyList;


    public PopupComponent(IFilterEditor editor,
                          Format format,
                          Comparator choicesComparator,
                          Comparator stringComparator) {
        historyModel = new HistoryListModel();
        choicesModel = new ChoicesListModel(format, choicesComparator,  
        		stringComparator);         		
        createGui(editor);
    }

    /** Invoked when the user select an element. */
    protected abstract void choiceSelected(Object selection);

    /** Returns the current selection -can be history or and choices-. */
    public Object getSelection() {
        return focusedList.getSelectedValue();
    }

    /** Returns the current choices size. */
    public int getChoicesSize() {
        return choicesModel.getSize();
    }

    /**
     * Adds content to the choices list.<br>
     * If there is no Renderer defined, the content is stringfied and sorted -so
     * duplicates are removed-
     */
    public void addChoices(Collection<?> choices, IChoicesParser parser) {
        if (choicesModel.addContent(choices, parser)) {
            hide();
        }
    }

    /** Adds content to the history list. */
    public void addHistory(Object st) {
        if (historyModel.add(st)) {
            hide();
        }
    }

    /** Clears the choices lists. */
    public void clearChoices() {
        choicesModel.clearContent();
        hide();
    }

    /** Clears the history list. */
    public void clearHistory() {
        historyModel.clear();
        hide();
    }

    /** Returns true if the popup is currently visible. */
    public boolean isVisible() {
        return (popup != null) && popup.isVisible();
    }

    /**
     * Displays the popup, if there is content (history or choices), and is not
     * yet visible<br>
     * It uses the passed component as guide to set the location and the size of
     * the popup.
     */
    public boolean display(Component editor) {
        if (isVisible()) {
            return false;
        }

        prepareGui();
        setPopupFocused(false);

        editorBoundsWatcher.displayPopup(editor);

        // Not yet knowing the focus, but the call to select (immediately after,
        // always), takes care of it
        focusedList = historyList;

        return true;
    }

    /** Hides the popup, returning false it was already hidden. */
    public boolean hide() {
        return editorBoundsWatcher.displayPopup(null);
    }

    public FilterListCellRenderer getFilterRenderer() {
        return listRenderer;
    }

    /** 
     * Specifies that the content requires no conversion to strings. 
     * @return true if choices should be added again.
     */
    public boolean setRenderedContent(ChoiceRenderer renderer,
                                      Comparator     choicesComparator,
                                      Comparator     stringComparator) {
        hide();
        listRenderer.setUserRenderer(renderer);
        boolean ret = choicesModel.setRenderedContent(choicesComparator, 
        		stringComparator);
        if (ret){
            historyModel.setStringContent(null);
        }
        return ret;
    }

    /** 
     * Specifies that the content is to be handled as strings.
     * @return true if choices should be added again.
     */
    public boolean setStringContent(Format             format,
    								Comparator         choicesComparator,
                                 Comparator<String> stringComparator) {
        hide();
        listRenderer.setUserRenderer(null);
        boolean ret = choicesModel.setStringContent(format, choicesComparator, 
        		stringComparator);
        if (ret){
            historyModel.setStringContent(stringComparator);
        }
        return ret;
    }

    /**
     * Returns the string comparator<br>
     * it will be invalid (null or not string comparator) for rendered content.
     */
    public Comparator<String> getStringComparator() {
        return choicesModel.getStringComparator();
    }

    /**
     * Finds -and selects- the best match to a given content, using the existing
     * history and choices.<br>
     * It always favor content belonging to the choices list, rather than to
     * the history list.
     *
     * @param   hint   an object used to select the match. If the content is
     *                 text-based (there is no Renderer defined), the hint is
     *                 considered the start of the string, and the best match
     *                 should start with the given hint). If the content is not
     *                 text-based, only exact matches are returned, no matter
     *                 the value of the parameter perfectMatch
     *
     * @return  a non null match (but its content can be null)
     */
    public ChoiceMatch selectBestMatch(Object hint) {
        ChoiceMatch hMatch = historyModel.getClosestMatch(hint);
        if (choicesModel.getSize() > 0) {
            ChoiceMatch match = choicesModel.getBestMatch(hint);
            if (isVisible() && (match.index >= 0)) {
                choicesList.ensureIndexIsVisible(match.index);
            }

            if (match.exact || (!hMatch.exact && (match.len >= hMatch.len))) {
                if (match.index >= 0) {
                    if (isVisible()) {
                        focusChoices();
                        select(match.index);
                    }

                }

                return match;
            }
        }

        if (hMatch.index != -1) {
            if (isVisible()) {
                focusHistory();
                select(hMatch.index);
            }
        }

        return hMatch;
    }

    /**
     * Returns the text that could complete the given string<br>
     * The completion string is the larger string that matches all existing
     * options starting with the given string.
     */
    public String getCompletion(String s) {
        return choicesModel.getCompletion(s, historyModel.getList());
    }

    /**
     * Informs that the focus is on the popup: this affects how the selected
     * elements are displayed
     */
    public void setPopupFocused(boolean set) {
        if (set != listRenderer.isFocusOnList()) {
            listRenderer.setFocusOnList(set);
            if(focusedList != null) {
                focusedList.repaint();
            }
        }
    }

    /** Returns true if the focus is currently on the popup. */
    public boolean isPopupFocused() {
        return isVisible() && listRenderer.isFocusOnList();
    }

    /** @see  IFilterEditor#setMaxHistory(int) */
    public void setMaxHistory(int size) {
        historyModel.setMaxHistory(Math.max(0, Math.min(size, maxVisibleRows)));
        hide();
    }

    /** @see  IFilterEditor#getMaxHistory() */
    public int getMaxHistory() {
        return historyModel.getMaxHistory();
    }
    
    public void setHistory(List<Object> history){
        historyModel.initialize(history);    	
    }

    public List<Object> getHistory(){
    	return historyModel.getShownHistory();
    }

    /**
     * Selects the first element in the focused list. If it is already on the
     * first element, or forceJump is true, selects the first element on the
     * history list.<br>
     * Returns true if there is indeed a change (or forceJump is true)
     */
    public boolean selectFirst(boolean forceJump) {
        boolean ret = canSwitchToHistory()
                && (forceJump || (choicesList.getSelectedIndex() == 0));
        if (ret) {
            focusHistory();
        }

        return select(0) || ret;
    }

    /**
     * Selects the last element in the focused list. If it is already on the
     * last element, or forceJump is true, selects the last element on the
     * choices list.<br>
     * Returns true if there is indeed a change (or forceJump is true)
     */
    public boolean selectLast(boolean forceJump) {
        boolean ret = canSwitchToChoices()
                && (forceJump
                    || (historyList.getSelectedIndex()
                        == (historyModel.getSize() - 1)));
        if (ret) {
            focusChoices();
        }

        return select(focusedList.getModel().getSize() - 1) || ret;
    }

    /**
     * If jumpRequired is true, or cannot move up on the focused list and the
     * focused list is the choices list, then move to the last element on the
     * history list.<br>
     * Otherwise, it just returns false
     */
    public boolean selectUp(boolean jumpRequired) {
        if (jumpRequired || !select(focusedList.getSelectedIndex() - 1)) {
            if (!canSwitchToHistory()) {
                return false;
            }

            focusHistory();
            select(historyModel.getSize() - 1);
        }

        return true;
    }

    /**
     * If jumpRequired is true, or cannot move down on the focused list and the
     * focused list is the history list, then move to the first visible element
     * on the choices list.<br>
     * Otherwise, it just returns false
     */
    public void selectDown(boolean jumpRequired) {
        if (jumpRequired || !select(focusedList.getSelectedIndex() + 1)) {
            if (canSwitchToChoices()) {
                focusChoices();
                select(choicesList.getFirstVisibleIndex());
            }
        }
    }

    /**
     * Moves down a page, or to the last element in the choices list, if needed.
     */
    public void selectDownPage() {
        if (isFocusInHistory()) {
            if (canSwitchToChoices()) {
                focusChoices();
            }

            select(focusedList.getLastVisibleIndex());
        } else {
            int lst = choicesList.getLastVisibleIndex();
            if (lst == choicesList.getSelectedIndex()) {
                lst = Math.min(lst + lst - choicesList.getFirstVisibleIndex(),
                        choicesModel.getSize() - 1);
            }

            select(lst);
        }
    }

    /**
     * Moves up a page, or to the first element in the history list, if needed.
     */
    public void selectUpPage() {
        int r = 0;
        if (!isFocusInHistory()) {
            int selected = choicesList.getSelectedIndex();
            if (canSwitchToHistory() && (selected == 0)) {
                focusHistory();
            } else {
                r = choicesList.getFirstVisibleIndex();
                if (r == selected) {
                    r = Math.max(0, r + r - choicesList.getLastVisibleIndex());
                }
            }
        }

        select(r);
    }

    /**
     * Selects the given row in the focused list.<br>
     * Returns true if there is a selection change
     */
    private boolean select(int n) {
        int current = focusedList.getSelectedIndex();
        setPopupFocused(true);
        if (n >= 0) {
            focusedList.setSelectedIndex(n);
            focusedList.ensureIndexIsVisible(n);
        }

        return current != focusedList.getSelectedIndex();
    }

    /**
     * Returns true if the focused list is the choices list and there is history
     * content.
     */
    private boolean canSwitchToHistory() {
        return (focusedList == choicesList) && historyScrollPane.isVisible();
    }

    /**
     * Returns true if the focused list is the history list and there is choices
     * content.
     */
    private boolean canSwitchToChoices() {
        return (focusedList == historyList) && choicesScrollPane.isVisible();
    }

    /** Moves the focus to the history list. */
    private void focusHistory() {
        choicesList.clearSelection();
        focusedList = historyList;
    }

    /** Moves the focus to the choices list. */
    private void focusChoices() {
        historyList.clearSelection();
        focusedList = choicesList;
    }

    /** Returns true if the focused list is the history list. */
    private boolean isFocusInHistory() {
        return focusedList == historyList;
    }

    /** Configures the popup panes to have the editor width. */
    void showPopup(Component editor) {
        int width = editor.getParent().getWidth() - 1;
        configurePaneSize(choicesScrollPane, width);
        configurePaneSize(historyScrollPane, width);
        popup.show(editor, -editor.getLocation().x - 1, editor.getHeight());
    }

    /** Configures the passed pane to have the given preferred width. */
    private void configurePaneSize(JComponent pane, int width) {
        Dimension size = pane.getPreferredSize();
        size.width = width;
        pane.setPreferredSize(size);
    }

    /**
     * Ensures that the height of the rows in the lists have the required size.
     */
    private void ensureListRowsHeight() {
        Object prototype;
        if ((listRenderer != null)
                && (listRenderer.getUserRenderer() == null)) {
            prototype = choicesList.getPrototypeCellValue();
            // we need to change the prototype. The jlist will not update its
            // cell height if the prototype does not change
            prototype = "X".equals(prototype) ? "Z" : "X"; //NON-NLS
        } else {
            prototype = null;
        }

        choicesList.setPrototypeCellValue(prototype);
        historyList.setPrototypeCellValue(prototype);
    }

    /** Creation of the popup's gui. */
    private void createGui(IFilterEditor editor) {
        MouseHandler mouseHandler = new MouseHandler();
        choicesList = new JList(choicesModel);
        choicesList.addMouseMotionListener(mouseHandler);
        choicesList.addMouseListener(mouseHandler);

        choicesScrollPane = createScrollPane(choicesList);

        historyList = new JList(historyModel);
        historyList.addMouseMotionListener(mouseHandler);
        historyList.addMouseListener(mouseHandler);

        choicesList.setBorder(null);
        choicesList.setFocusable(false);
        choicesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        historyList.setBorder(null);
        historyList.setFocusable(false);
        historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        ensureListRowsHeight();

        separator = new JSeparator();

        popup = new JPopupMenu();
        popup.setLayout(new BorderLayout());
        popup.setBorderPainted(true);
        popup.setOpaque(false);
        popup.addPopupMenuListener(this);

        historyScrollPane = createScrollPane(historyList);

        popup.add(historyScrollPane, BorderLayout.NORTH);
        popup.add(separator, BorderLayout.CENTER);
        popup.add(choicesScrollPane, BorderLayout.SOUTH);
        popup.setDoubleBuffered(true);
        popup.setFocusable(false);

        listRenderer = new FilterListCellRenderer(editor, choicesList);
        choicesList.setCellRenderer(listRenderer);
        historyList.setCellRenderer(listRenderer);
    }

    /**
     * Reconfigures the gui, ensuring the correct size of the history and
     * choices lists.
     */
    private void prepareGui() {
        // The history size PLUS choices size must be below `maxVisibleRows`
        // (note that the history size is always lower than `maxVisibleRows`,
        // and that it should be, if possible, equal to 'maxPopupHistory')
        // In addition, the history should not show any of the choices that
        // are visible, when all choices can be displayed at once
        int historySize = historyModel.clearRestrictions(); // restrict none
        int choicesSize = choicesModel.getSize();
        int maxChoices = Math.min(choicesSize, maxVisibleRows - historySize);
        if ((historySize > 0) && (choicesSize <= maxChoices)) {
            for (int i = 0; i < choicesSize; i++) {
                if (historyModel.restrict(choicesModel.getElementAt(i))) {
                    --historySize;
                }
            }

            maxChoices = choicesSize;
        }

        boolean showHistory = historySize > 0;
        boolean showChoices = maxChoices > 0;
        choicesScrollPane.setVisible(showChoices);
        historyScrollPane.setVisible(showHistory);
        if (showHistory) {
            historyList.setVisibleRowCount(historySize);
            historyScrollPane.setPreferredSize(null);
        }

        if (showChoices) { // in fact, there are always choices
            choicesList.setVisibleRowCount(maxChoices);
            choicesScrollPane.setPreferredSize(null);
        }

        separator.setVisible(showHistory && showChoices);
    }

    private JScrollPane createScrollPane(JList list) {
        JScrollPane ret = new JScrollPane(list,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        ret.setHorizontalScrollBar(null);
        ret.setFocusable(false);
        ret.getVerticalScrollBar().setFocusable(false);
        ret.setBorder(null);

        return ret;
    }

    @Override public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
        cancelReason = null;
    }

    @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
        // no need to react to this event
    }

    @Override public void popupMenuCanceled(PopupMenuEvent e) {
        AWTEvent ev = EventQueue.getCurrentEvent();
        if (ev instanceof MouseEvent) {
            cancelReason = ev.getSource();
        }
    }

    /**
     * Returns the source of the event than canceled last time the popup menu.
     */
    public boolean isMenuCanceledForMouseEvent(Object source) {
        boolean ret = !popup.isVisible() && (cancelReason == source);
        cancelReason = null;

        return ret;
    }

    /**
     * The mouse handler will select automatically the choice under the mouse,
     * and passes directly the focus to the popup under the mouse.
     */
    final class MouseHandler extends MouseAdapter {
        @Override public void mouseClicked(MouseEvent e) {
            setPopupFocused(true);
            listSelection(focusedList.getSelectedValue());
        }

        @Override public void mouseMoved(MouseEvent e) {
            setPopupFocused(true);

            JList focus = (JList) e.getSource();
            JList other = (focus == choicesList) ? historyList : choicesList;
            focus.setSelectedIndex(focus.locationToIndex(e.getPoint()));
            if (other.getModel().getSize() > 0) {
                other.setSelectedIndex(0); // silly, but needed
                other.clearSelection();
            }

            focusedList = focus;
        }

        private void listSelection(Object object) {
            choiceSelected(object);
            hide();
        }
    }

    /** Class to track changes in position or size on the popup's editor. */
    final class EditorBoundsWatcher extends ComponentAdapter {

        private Component editor;

        /**
         * Displays or hides the popup, associated to the given editor.
         *
         * @param   editor  null to hide the popup
         *
         * @return  true if editor is null and the popup was visible
         */
        public boolean displayPopup(Component editor) {
            if (editor != this.editor) {
                if (this.editor != null) {
                	this.editor.removeComponentListener(this);
                }

                if (editor != null) {
                    editor.addComponentListener(this);
                }
            }

            this.editor = editor;
            if (editor != null) {
                showPopup(editor);
            } else if (popup.isVisible()) {
                popup.setVisible(false);
                return true;
            }

            return false;
        }

        private void handleChange() {
            if (popup.isVisible()) {
                showPopup(editor);
            } else {
                displayPopup(null);
            }
        }

        @Override public void componentMoved(ComponentEvent e) {
            handleChange();
        }

        @Override public void componentResized(ComponentEvent e) {
            handleChange();
        }
    }
}
