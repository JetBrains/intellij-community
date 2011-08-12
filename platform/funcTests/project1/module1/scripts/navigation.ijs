%action CloseProject
%call openProjectClone(/Users/kirillk/idea/community/platform/funcTests/project1)
%stop
%action GotoClass
PsiManager\n
%action SelectIn
1\n
%call printFocus()