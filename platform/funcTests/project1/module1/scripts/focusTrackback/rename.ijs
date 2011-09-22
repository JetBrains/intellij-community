%startTest Rename class

%include ../include/project1Init.ijs
%action GotoClass
ClassWithManyImports\n
%call checkFocus(editorTab=ClassWithManyImports.java)
%action RenameElement
%[right]
1\n
%call printFocus()
%%call checkFocus(editorTab=ClassWithManyImports1.java)

%endTest