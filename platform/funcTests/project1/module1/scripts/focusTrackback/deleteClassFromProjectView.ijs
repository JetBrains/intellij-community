%startTest Delete class from project view

%include ../include/project1Init.ijs
%action GotoClass
ActiveLibrary\n
%call checkFocus(editorTab=ActiveLibrary.java)
%action GotoClass
VisualFabrique\n
%call checkFocus(editorTab=VisualFabrique.java)

%action SelectIn
%[right]
1\n
%call checkFocus(selectedNodes=VisualFabrique)

%action $Delete
\n
%%todo
%%call checkFocus(selectedNodes=BusinessObjectModel)

%endTest