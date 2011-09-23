%startTest Select in package view
%include ../include/project1Init.ijs

%action GotoClass
PsiManager\n
%action SelectIn
%[right]
2\n
%call checkFocus(selectedNodes=PsiManager|toolWindowTitle=com.intellij.testProject.idea.PsiManager|toolWindowTab=com.intellij.testProject.idea.PsiManager)
%endTest