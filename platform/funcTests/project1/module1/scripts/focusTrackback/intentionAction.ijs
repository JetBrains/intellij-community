%startTest Intention action

%include ../include/project1Init.ijs

%action GotoClass
FileEditorManager\n
%action EditorLineEnd
%action EditorEnter
String text = 1;

%% Some magic moves to make code analyzer finish (todo: cdr)
%delay 1000
%[left]
%delay 1000
%[left]
%call waitDaemonForFinish()


%action ShowIntentionActions
change\n
%call checkFocus(editorTab=FileEditorManager.java)
%call assertEditorLine(    int text = <caret>1;)
%endTest