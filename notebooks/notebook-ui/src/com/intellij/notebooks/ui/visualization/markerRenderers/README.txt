The renderers are to be documented here.

[NotebookLineMarkerRenderer] - abstract class, parent to classes mentioned below

[NotebookCodeCellBackgroundLineMarkerRenderer] - vertical gray rectangle in the gutter area between the line numbers and the text.
[NotebookAboveCodeCellGutterLineMarkerRenderer] - little gray rectangle in the gutter area on the left of the above cell panel
[NotebookBelowCellCellGutterLineMarkerRenderer] - little gray rectangle in the gutter area on the left of the below cell panel
[NotebookCellHighlighterRenderer] - the cell background itself + clips this highlight under the editor's scroll bar area

[NotebookCellToolbarGutterLineMarkerRenderer] - grey gutter rectangle on the left of the SQL cells toolbar (the one on top of the cell)
[NotebookTextCellBackgroundLineMarkerRenderer] - (?) R notebooks related

[WIP: MarkdownCellBackgroundLineMarkerRenderer*] - vertical gray line for markdown cells in the gutter, between the line numbers and cell body
