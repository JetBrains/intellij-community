class Test {
  public void testCopyPasteWithoutUnnecessaryIndent() throws Exception {
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    int oldValue = settings.REFORMAT_ON_PASTE;
    settings.REFORMAT_ON_PASTE = CodeInsightSettings.INDENT_BLOCK;
    try {
      doTest(TestFileType.TEXT                                               <caret>,
              new Runnable() {
        @Override
        public void run() {
          // Move caret to the non-zero column.
          myEditor.getCaretModel().moveToOffset(3);

          // Select all text.
          myEditor.getSelectionModel().setSelection(0, myEditor.getDocument().getTextLength());

          // Perform 'copy-paste' action. Expecting to get the same file.
          copy();
          paste();
        }
      });
    }
    finally {
      settings.REFORMAT_ON_PASTE = oldValue;
    }
  }
}