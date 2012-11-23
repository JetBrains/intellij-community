package org.hanuna.gitalk.controller;

import org.hanuna.gitalk.printmodel.PrintCellRow;

/**
 * @author erokhins
 */
public class GraphTableCell {
    private final PrintCellRow row;
    private final String text;

    public GraphTableCell(PrintCellRow row, String text) {
        this.row = row;
        this.text = text;
    }

    public PrintCellRow getRow() {
        return row;
    }

    public String getText() {
        return text;
    }
}
