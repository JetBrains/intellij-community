!define INSTALL_OPTION_ELEMENTS 4


Function customInstallActions
  DetailPrint "There are no custom install actions."
FunctionEnd


Function ConfirmDesktopShortcut
  !insertmacro MUI_HEADER_TEXT "$(installation_options)" "$(installation_options_prompt)"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 1" "Text" "$(create_desktop_shortcut)"
  call winVersion
  ${If} $0 == "1"
    !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 2" "Type" "Label"
    !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 2" "Text" ""
  ${Else}
    !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 2" "Text" "$(create_quick_launch_shortcut)"
  ${EndIf}
  StrCmp "${ASSOCIATION}" "NoAssociation" skip_association
  StrCpy $R0 3
  push "${ASSOCIATION}"
loop:
  call SplitStr
  Pop $0
  StrCmp $0 "" done
  IntOp $R0 $R0 + 1
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $R0" "Text" "$0"
  goto loop
skip_association:
  StrCpy $R0 2
  call winVersion
  ${If} $0 == "1"
  IntOp $R0 $R0 - 1
  ${EndIf}
done:
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Settings" "NumFields" "$R0"
  !insertmacro INSTALLOPTIONS_DISPLAY "Desktop.ini"
FunctionEnd
