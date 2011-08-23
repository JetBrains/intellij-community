%action CloseProject
%call openProjectClone(../community/platform/funcTests/project1)
%action GotoClass
PsiManager\n
%action SelectIn
%[right]
1\n
%call checkFocus(selectedNodes=PsiManager|toolWindowTitle=com.intellij.testProject.idea.PsiManager|toolWindowTab=com.intellij.testProject.idea.PsiManager)