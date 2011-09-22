%startTest Typeahead in choose by name popup
%include ../include/project1Init.ijs

%action GotoClass
PsiManager\n
%call checkFocus(editorTab=PsiManager.java)
%action GotoClass
ActiveLibrary\n
%call checkFocus(editorTab=ActiveLibrary.java)
%endTest
