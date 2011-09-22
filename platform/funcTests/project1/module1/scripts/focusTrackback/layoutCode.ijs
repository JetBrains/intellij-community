%startTest Layout all code in directory, invoked from editor, focus must be back to editor

%include ../include/project1Init.ijs
%action GotoClass
ClassWithManyImports\n
%action ActivateProjectToolWindow
%call flushUi()
%[escape]
%call checkFocus(editorTab=ClassWithManyImports.java)
%action OptimizeImports
%[alt a]
%call flushUi()
%[space]
\n
%call checkFocus(editorTab=ClassWithManyImports.java)

%endTest