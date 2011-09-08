%startTest Create new class from project view

%include ../include/project1Init.ijs

%action GotoClass
PsiManager\n
%action SelectIn
%[right]
1\n
%call waitForToolWindow(Project)

%stop
%[escape]
%action NewElement
Java\n
NewClass
%endTest