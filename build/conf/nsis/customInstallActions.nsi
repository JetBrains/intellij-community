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
  ${StrRep} $0 ${PRODUCT_EXE_FILE} "64.exe" ".exe"
  ${If} $0 == ${PRODUCT_EXE_FILE}
    StrCpy $R0 "32-bit launcher"
    StrCpy $R1 "64-bit launcher"
  ${Else}
    ;there is only one launcher and it is 64-bit.
    StrCpy $R0 "64-bit launcher"
    StrCpy $R1 ""
  ${EndIf}
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 2" "Text" $R0

  ${If} $R1 != ""
    ${StrRep} $R0 ${PRODUCT_EXE_FILE_64} "64.exe" ".exe"
    ${If} $R0 == ${PRODUCT_EXE_FILE}
      call searchJava64
      ${If} $3 != ""
        !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 3" "Type" "checkbox"
        !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 3" "Text" $R1
      ${EndIf}
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
  IntOp $R0 ${INSTALL_OPTION_ELEMENTS} - 1
done:
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Settings" "NumFields" "$R0"
  !insertmacro INSTALLOPTIONS_DISPLAY "Desktop.ini"
FunctionEnd
