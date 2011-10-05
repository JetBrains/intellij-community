%startTest Double shortcut do not type into editor

%include ../include/project1Init.ijs
%action GotoClass
PsiManager\n
%call checkFocus(editorTab=PsiManager.java)
%action GotoLine
12\n
%action EditorLineStart
%call checkFocus(caret=11:0|editorTab=PsiManager.java)
%call assertEditorLine(<caret>)
%[control f]
%delay 1000
l
%call assertEditorLine(<caret>)
%endTest