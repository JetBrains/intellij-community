%action GotoClass
PsiManager\n
%%call checkFocus(editorTab=PsiManager.java)

%action ShowNavBar
%call checkFocus(navBar=module1>src>com>intellij>testProject>idea>[PsiManager])

%[left]
%[left]
%[down]
%call checkFocus(navBar=module1>src>com>intellij>[testProject]>idea>PsiManager|navBarPopup=fabrique>[idea]>mps)
\n
%call flushUi()
\n
%call checkFocus(editorTab=ClassWithManyImports.java)

