%startTest Create superclass

%%include ../include/project1Init.ijs
%%action GotoClass
%%PsiManager\n

%include ../include/init.ijs
%call contextMenu(Refactor|Extract Superclass)
TestSuperclass\n
%call checkFocus(dialog=Analyze and Replace Usages)

%endTest