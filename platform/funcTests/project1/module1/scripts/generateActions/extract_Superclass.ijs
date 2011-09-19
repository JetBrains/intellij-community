%startTest Create superclass from popup menu

%include ../include/project1Init.ijs
%action GotoClass
FileEditorManager\n
%call checkFocus(editorTab=FileEditorManager.java)
%call contextMenu(Refactor|Extract Superclass)
TestSuperclass\n
%call waitForDialog(Analyze and Replace Usages)
%[space]
%call waitForDialog(Use Interface Where Possible)
%[space]
%call checkFocus(editorTab=FileEditorManager.java)

%endTest