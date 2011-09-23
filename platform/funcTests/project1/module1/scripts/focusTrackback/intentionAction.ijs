%startTest Intention action

%include ../include/project1Init.ijs

%action GotoClass
FileEditorManager\n
%action EditorLineEnd
%action EditorEnter
String text = 1;
%[left]
%call waitForEditorHint()
%action ShowIntentionActions
%endTest