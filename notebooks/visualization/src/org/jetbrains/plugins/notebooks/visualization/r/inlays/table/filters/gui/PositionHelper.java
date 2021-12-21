/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.gui;

import com.intellij.ui.Gray;
import org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.gui.TableFilterHeader.Position;

import javax.swing.*;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * <p>Helper class to locate the filter header on the expected place by the
 * table header</p>
 */
class PositionHelper implements PropertyChangeListener {

    /** This variable defines how to handle the position of the header. */
    Position location;

    /**
     * The viewport associated to this header. It is null if the location is not
     * automatically managed
     */
    JViewport headerViewport;

    /** The previous viewport of the associated table. */
    Component previousTableViewport;

    /** The handled filter header. */
    TableFilterHeader filterHeader;

    public PositionHelper(TableFilterHeader filterHeader) {
        this.filterHeader = filterHeader;
    }


    /** Sets the position of the header related to the table. */
    public void setPosition(Position location) {
        this.location = location;

        JTable table = filterHeader.getTable();
        changeTable(table, table);
    }


    /** Returns the mode currently associated to the TableHeader. */
    public Position getPosition() {
        return location;
    }

    /** The associated TableFilterHeader reports a change on its visibility. */
    public void headerVisibilityChanged(boolean visible) {
        JTable table = filterHeader.getTable();
        changeTable(table, null);
        if (visible && (table != null)) {
            changeTable(null, table);
        }
    }


    /**
     * The filter header reports that the table being handled is going to
     * change.
     */
    public void changeTable(JTable oldTable, JTable newTable) {
        if (oldTable != null) {
            oldTable.removePropertyChangeListener("ancestor", this);
        }

        cleanUp();
        if (newTable != null) {
            newTable.addPropertyChangeListener("ancestor", this);
            trySetUp(newTable);
        }
    }

    /** Method automatically invoked when the class ancestor changes. */
    public void filterHeaderContainmentUpdate() {
        if (!canHeaderLocationBeManaged()) {
            cleanUp();
        }
    }

    /** PropertyChangeListener interface. */
    @Override public void propertyChange(PropertyChangeEvent evt) {

        // the table has changed containment. clean up status and prepare again,
        // if possible; however, do nothing if the current setup is fine
        if ((previousTableViewport != evt.getNewValue()) || (evt.getSource() != filterHeader.getTable())) {
            previousTableViewport = null;
            cleanUp();
            trySetUp(filterHeader.getTable());
        }
    }

    /**
     * Returns true if the header location can be automatically controlled.
     *
     * @return  false if the component has been explicitly included in a
     *          container
     */
    private boolean canHeaderLocationBeManaged() {
        if (location == Position.NONE) {
            return false;
        }

        Container parent = filterHeader.getParent();

        return (parent == null) || (parent == headerViewport);
    }


    /** Tries to setup the filter header automatically for the given table. */
    private void trySetUp(JTable table) {
        if ((table != null) && table.isVisible() && canHeaderLocationBeManaged()
                && filterHeader.isVisible()) {
            Container p = table.getParent();
            if (p instanceof JViewport) {
                Container gp = p.getParent();
                if (gp instanceof JScrollPane) {
                    JScrollPane scrollPane = (JScrollPane) gp;
                    JViewport viewport = scrollPane.getViewport();
                    if ((viewport != null) && (viewport.getView() == table)) {
                        setUp(scrollPane);
                        previousTableViewport = p;
                    }
                }
            }
        }
    }

    /**
     * Sets up the header, placing it on a new viewport for the given
     * Scrollpane.
     */
    private void setUp(JScrollPane scrollPane) {
        headerViewport = new JViewport() {

            private static final long serialVersionUID = 7109623726722227105L;

            @Override public void setView(Component view) {
                // if the view is not a table header, somebody is doing
                // funny stuff. not much to do!
                if (view instanceof JTableHeader) {
                    removeTableHeader();
                    // the view is always added, even if set non visible
                    // this way, it can be recovered if the position changes
                    view.setVisible(location != Position.REPLACE);
                    ((JTableHeader)view).setOpaque(false);
                    view.setBackground(Gray.TRANSPARENT);
                    filterHeader.add(view, location == Position.INLINE ? BorderLayout.NORTH : BorderLayout.SOUTH);
                    filterHeader.revalidate();
                    super.setView(filterHeader);
                }
            }

            /**
             * Removes the current JTableHeader in the filterHeader, returning
             * it. it does nothing if there is no such JTableHeader
             */
            private Component removeTableHeader() {
                Component tableHeader = getTableHeader();
                if (tableHeader != null) {
                    filterHeader.remove(tableHeader);
                }

                return tableHeader;
            }

        };

        JViewport currentColumnHeader = scrollPane.getColumnHeader();
        if (currentColumnHeader != null) {
            // this happens if the table has not been yet added to the
            // scrollPane
            Component view = currentColumnHeader.getView();
            if (view != null) {
                headerViewport.setView(view);
            }
        }

        scrollPane.setColumnHeader(headerViewport);
    }

    /** Removes the current viewport, setting it up to clean status. */
    private void cleanUp() {
        JViewport currentViewport = headerViewport;
        headerViewport = null;
        if (currentViewport != null) {
            currentViewport.remove(filterHeader);

            Container parent = currentViewport.getParent();
            if (parent instanceof JScrollPane) {
                JScrollPane scrollPane = (JScrollPane) parent;
                if (scrollPane.getColumnHeader() == currentViewport) {
                    Component tableHeader = getTableHeader();
                    JViewport newView = (tableHeader == null) ? null : createCleanViewport(tableHeader);
                    scrollPane.setColumnHeader(newView);
                }
            }
        }
    }

    /** Creates a simple JViewport with the given component as view. */
    private JViewport createCleanViewport(Component tableHeader) {
        JViewport ret = new JViewport();
        ret.setView(tableHeader);

        return ret;
    }

    /** Returns the JTableHeader in the filterHeader, if any. */
    Component getTableHeader() {
        for (Component component : filterHeader.getComponents()) {
            // there should be just one (the header's controller)
            // or two components (with the table header)
            if (component instanceof JTableHeader) {
                return component;
            }
        }

        return null;
    }

}
