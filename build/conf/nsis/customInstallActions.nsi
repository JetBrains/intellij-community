!define INSTALL_OPTION_ELEMENTS 4
!define JAVA_REQUIREMENT 1.8

Function customInstallActions
  DetailPrint "There are no custom install actions."
FunctionEnd

Function searchJava64
  StrCpy $0 "HKLM"
  StrCpy $1 "Software\JavaSoft\Java Development Kit\${JAVA_REQUIREMENT}"
  StrCpy $2 "JavaHome"
  SetRegView 64
  call OMReadRegStr
  SetRegView 32
  StrCpy $3 "$3\bin\java.exe"
  IfFileExists $3 done no_java_64
no_java_64:
  StrCpy $3 ""
done:
FunctionEnd

Function ConfirmDesktopShortcut
  !insertmacro MUI_HEADER_TEXT "$(installation_options)" "$(installation_options_prompt)"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 2" "Text" "${PRODUCT_EXE_FILE}"
  ${StrRep} $R0 ${PRODUCT_EXE_FILE_64} "64.exe" ".exe"
  ${If} $R0 == ${PRODUCT_EXE_FILE}
    call searchJava64
    ${If} $3 != ""
      !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 3" "Type" "checkbox"
      !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 3" "Text" "${PRODUCT_EXE_FILE_64}"
      !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 3" "State" "0"
    ${EndIf}
  ${EndIf}
  StrCmp "${ASSOCIATION}" "NoAssociation" skip_association
  StrCpy $R0 ${INSTALL_OPTION_ELEMENTS}
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
