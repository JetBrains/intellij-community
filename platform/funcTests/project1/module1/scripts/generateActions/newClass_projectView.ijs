%startTest Create new class from project view

%include ../include/project1Init.ijs

%action GotoClass
PsiManager\n
%action SelectIn
%[right]
1\n
%call flushUi()

%action NewElement
Java\n
NewClass\n
%call checkFocus(editorTab=NewClass.java|caret=9:21)
%endTest