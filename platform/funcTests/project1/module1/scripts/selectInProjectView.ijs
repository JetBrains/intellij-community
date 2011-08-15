%% Features tested:
%%       - typahead in the "Goto class" dialog
%%       - select in an unitialized project view pane (project, packages)
%action CloseProject
%call openProjectClone(../community/platform/funcTests/project1)
%action GotoClass
PsiManager\n
%action SelectIn
%[right]
1\n
%call checkFocus(selectedNodes=PsiManager|toolWindowTitle=com.intellij.testProject.idea.PsiManager|toolWindowTab=com.intellij.testProject.idea.PsiManager)