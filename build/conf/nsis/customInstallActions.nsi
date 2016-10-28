!include x64.nsh
!define INSTALL_OPTION_ELEMENTS 5
!define JAVA_REQUIREMENT 1.8
!define BINTRAY "https://bintray.com/jetbrains/intellij-jdk"
!define JETBRAINS "https://download.jetbrains.com"
Var linkToJre64

; This function does a case sensitive searches for an occurrence of a substring in a string.
; It returns the substring if it is found.
; Otherwise it returns null("").
; http://nsis.sourceforge.net/StrContains
!macro _StrContainsConstructor OUT NEEDLE HAYSTACK
  Push `${HAYSTACK}`
  Push `${NEEDLE}`
  Call StrContains
  Pop `${OUT}`
!macroend
!define StrContains '!insertmacro "_StrContainsConstructor"'

Var STR_HAYSTACK
Var STR_NEEDLE
Var STR_CONTAINS_VAR_1
Var STR_CONTAINS_VAR_2
Var STR_CONTAINS_VAR_3
Var STR_CONTAINS_VAR_4
Var STR_RETURN_VAR

Function StrContains
  Exch $STR_NEEDLE
  Exch 1
  Exch $STR_HAYSTACK
  ; Uncomment to debug
  ; MessageBox MB_OK 'STR_NEEDLE = $STR_NEEDLE STR_HAYSTACK = $STR_HAYSTACK '
    StrCpy $STR_RETURN_VAR ""
    StrCpy $STR_CONTAINS_VAR_1 -1
    StrLen $STR_CONTAINS_VAR_2 $STR_NEEDLE
    StrLen $STR_CONTAINS_VAR_4 $STR_HAYSTACK
    loop:
      IntOp $STR_CONTAINS_VAR_1 $STR_CONTAINS_VAR_1 + 1
      StrCpy $STR_CONTAINS_VAR_3 $STR_HAYSTACK $STR_CONTAINS_VAR_2 $STR_CONTAINS_VAR_1
      StrCmp $STR_CONTAINS_VAR_3 $STR_NEEDLE found
      StrCmp $STR_CONTAINS_VAR_1 $STR_CONTAINS_VAR_4 done
      Goto loop
    found:
      StrCpy $STR_RETURN_VAR $STR_NEEDLE
      Goto done
    done:
   Pop $STR_NEEDLE ;Prevent "invalid opcode" errors and keep the
   Exch $STR_RETURN_VAR
FunctionEnd


Function customInstallActions
  !insertmacro INSTALLOPTIONS_READ $R0 "Desktop.ini" "Field 4" "State"
  ${If} $R0 == 1
    inetc::get $linkToJre64 "$TEMP\jre64.tar.gz" /END
    Pop $0
    ${If} $0 == "OK"
      untgz::extract "-d" "$INSTDIR\jre64" "$TEMP\jre64.tar.gz"
      StrCmp $R0 "success" removeTempJre64
      DetailPrint "Failed to extract jre64.tar.gz"
      MessageBox MB_OK|MB_ICONEXCLAMATION|MB_DEFBUTTON1 "Failed to extract $TEMP\jre64.tar.gz"
removeTempJre64:
      IfFileExists "$TEMP\jre64.tar.gz" 0 done
      Delete "$TEMP\jre64.tar.gz"
    ${Else}
      MessageBox MB_OK|MB_ICONEXCLAMATION "The $linkToJre64 download is failed: $0"
    ${EndIf}
  ${EndIf}
done:
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
        ; if 64-bit Win OS and jre64 for the build is available then add checkbox to Installation Options dialog
        ${If} ${RunningX64}
          ${GetParameters} $R0
          ClearErrors
          ${GetOptions} $R0 "jre64=" $R1
          ${IfNot} ${Errors}
            call ValidateLinkToJre64
            StrCmp $0 "OK" addCheckBoxToDialog
          ${EndIf}
          StrCmp "${LINK_TO_JRE64}" "null" association 0
          StrCpy $R1 ${LINK_TO_JRE64}
          call ValidateLinkToJre64
          ${If} $0 == "OK"
addCheckBoxToDialog:
            !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 4" "Type" "checkbox"
            !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 4" "Text" "Download and install 64-bit JRE by JetBrains (will be used with 64-bit launcher)"
          ${EndIf}
        ${EndIf}
      ${EndIf}
    ${EndIf}
  ${EndIf}
association:
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

Function ValidateLinkToJre64
  ${StrContains} $R0 ${JETBRAINS} $R1
  StrCmp $R0 "" 0 checkIfFileExists
  ${StrContains} $R0 ${BINTRAY} $R1
  ${If} $R0 != ""
checkIfFileExists:
    StrCpy $linkToJre64 $R1
    inetc::head /SILENT /TOSTACK $linkToJRE64 "" /END
    Pop $0
  ${Else}
    StrCpy $linkToJre64 ""
  ${EndIf}
FunctionEnd