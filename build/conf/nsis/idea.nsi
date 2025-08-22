Unicode true
ManifestDPIAware true
SetCompressor lzma
RequestExecutionLevel user

!define "__check_${NSIS_MAX_STRLEN}"
!ifndef "__check_8192"
  !error "'strlen_8192' build is required to compile this script (see 'NSIS-upgrade.md'). NSIS_MAX_STRLEN=${NSIS_MAX_STRLEN}."
!endif
!undef "__check_${NSIS_MAX_STRLEN}"

!addplugindir "${NSIS_DIR}\Plugins\x86-unicode"
!addincludedir "${NSIS_DIR}\Include"

!include FileFunc.nsh
!include InstallOptions.nsh
!include LogicLib.nsh
!include MUI2.nsh
!include StrFunc.nsh
!include TextFunc.nsh
!include UAC.nsh
!include WinVer.nsh
!include x64.nsh

; `StrFunc.nsh` requires priming the commands which actually get used later
${StrStr}
${UnStrStr}
${StrLoc}
${UnStrRep}

!include "log.nsi"
!include "registry.nsi"
!include "config.nsi"
!include "customInstallActions.nsi"

Name "${MUI_PRODUCT}"

OutFile "${OUT_DIR}\${OUT_FILE}.exe"

!define DEFAULT_INST_DIR "$PROGRAMFILES64\${MANUFACTURER}\${INSTALL_DIR_AND_SHORTCUT_NAME}"
InstallDir "${DEFAULT_INST_DIR}"

!define /date CURRENT_YEAR "%Y"
VIAddVersionKey /LANG=0 "CompanyName" "JetBrains s.r.o."
VIAddVersionKey /LANG=0 "FileDescription" "${MUI_PRODUCT} Windows Installer"
VIAddVersionKey /LANG=0 "FileVersion" "${VER_BUILD}"
VIAddVersionKey /LANG=0 "LegalCopyright" "Copyright 2000-${CURRENT_YEAR} JetBrains s.r.o."
VIAddVersionKey /LANG=0 "ProductName" "${MUI_PRODUCT}"
VIAddVersionKey /LANG=0 "ProductVersion" "${MUI_VERSION_MAJOR}.${MUI_VERSION_MINOR}"
VIFileVersion ${FILE_VERSION_NUM}
VIProductVersion ${PRODUCT_VERSION_NUM}

; Product with version (IntelliJ IDEA #xxxx).
; Used in registry to put each build info into the separate subkey
; Add&Remove programs doesn't understand subkeys in the Uninstall key,
; thus ${PRODUCT_WITH_VER} is used for uninstall registry information
!define PRODUCT_REG_VER "${MUI_PRODUCT}\${VER_BUILD}"

Var startMenuFolder
Var productLauncher
Var baseRegKey
Var silentMode

; position of controls for Uninstall Old Installations dialog
Var control_fields
Var max_fields
Var bottom_position
Var max_length
Var line_height
Var extra_space

; position of controls for Installation Options dialog
Var launcherShortcut
Var addToPath
Var updateContextMenu

ReserveFile "desktop.ini"
ReserveFile "DeleteSettings.ini"
ReserveFile "UninstallOldVersions.ini"
!insertmacro MUI_RESERVEFILE_LANGDLL

!define MUI_ICON "${IMAGES_LOCATION}\${PRODUCT_ICON_FILE}"
!define MUI_UNICON "${IMAGES_LOCATION}\${PRODUCT_UNINSTALL_ICON_FILE}"

!define MUI_HEADERIMAGE
!define MUI_HEADERIMAGE_BITMAP "${IMAGES_LOCATION}\${PRODUCT_HEADER_FILE}"
!define MUI_WELCOMEFINISHPAGE_BITMAP "${IMAGES_LOCATION}\${PRODUCT_LOGO_FILE}"


!macro INST_UNINST_SWITCH un
  Function ${un}SplitStr
    Exch $0 ; str
    Push $1 ; inQ
    Push $3 ; idx
    Push $4 ; tmp
    StrCpy $1 0
    StrCpy $3 0
  loop:
    StrCpy $4 $0 1 $3
    ${If} $4 == '"'
      ${If} $1 <> 0
        StrCpy $0 $0 "" 1
        IntOp $3 $3 - 1
      ${EndIf}
      IntOp $1 $1 !
    ${EndIf}
    ${If} $4 == '' ; The end?
      StrCpy $1 0
      StrCpy $4 ','
    ${EndIf}
    ${If} $4 == ','
    ${AndIf} $1 = 0
      StrCpy $4 $0 $3
      StrCpy $1 $4 "" -1
      ${IfThen} $1 == '"' ${|} StrCpy $4 $4 -1 ${|}
  killspace:
      IntOp $3 $3 + 1
      StrCpy $0 $0 "" $3
      StrCpy $1 $0 1
      StrCpy $3 0
      StrCmp $1 ',' killspace
      Push $0 ; Remaining
      Exch 4
      Pop $0
      ${If} $4 == ""
        Pop $4
        Pop $3
        Pop $1
        Return
      ${EndIf}
      Exch $4
      Exch 2
      Pop $1
      Pop $3
      Return
    ${EndIf}
    IntOp $3 $3 + 1
    Goto loop
  FunctionEnd

  Function ${un}adjustLanguage
    ${If} $Language == ${LANG_SIMPCHINESE}
      System::Call 'kernel32::GetUserDefaultUILanguage() h .r10'
      ${If} $R0 != ${LANG_SIMPCHINESE}
        ${LogText} "Language override: $R0 != ${LANG_SIMPCHINESE}"
        StrCpy $Language ${LANG_ENGLISH}
      ${EndIf}
    ${EndIf}
  FunctionEnd

  Function ${un}postEnvChangeEvent
    DetailPrint "Notifying applications about environment changes..."
    ; SendMessageTimeout(HWND_BROADCAST, WM_SETTINGCHANGE, 0, (LPARAM)"Environment", SMTO_ABORTIFHUNG, 5000, &dwResult)
    System::Call 'user32::SendMessageTimeout(i 0xFFFF, i 0x1A, i 0, t "Environment", i 0x2, i 1000, *i .r1) i .r0'
    IntFmt $0 "0x%x" $0
    DetailPrint "  SendMessageTimeout(): $0, $1"
  FunctionEnd
!macroend

!insertmacro INST_UNINST_SWITCH ""
!insertmacro INST_UNINST_SWITCH "un."


; checking whether there are files in the $INSTDIR
Function OnDirectoryPageLeave
  StrCpy $0 "$INSTDIR"
  StrCpy $9 ""
  Call isDirEmpty
  ${If} $9 != ""
    ${LogText} "ERROR: installation dir is not empty: $INSTDIR"
    MessageBox MB_OK|MB_ICONEXCLAMATION "$(choose_empty_folder)"
    Abort
  ${EndIf}
FunctionEnd

; recursively checking whether there are files in the $0, ignoring empty subdirectories
Function isDirEmpty
  Push $1
  Push $2
  ClearErrors
  FindFirst $1 $2 "$0\*.*"
  ${DoUntil} ${Errors}
    ${If} $2 != "."
    ${AndIf} $2 != ".."
      ${If} ${FileExists} "$0\$2\*.*"
        Push $0
        StrCpy "$0" "$0\$2"
        Call isDirEmpty
        Pop $0
        ${If} $9 != ""
          ${Break}
        ${EndIf}
      ${Else}
        ${LogText} "isDirEmpty: found '$0\$2'"
        StrCpy $9 "not empty"
        ${Break}
      ${EndIf}
    ${EndIf}
    FindNext $1 $2
  ${Loop}
  FindClose $1
  Pop $2
  Pop $1
FunctionEnd


Function getInstallationOptionsPositions
  !insertmacro INSTALLOPTIONS_READ $launcherShortcut "Desktop.ini" "Settings" "DesktopShortcut"
  !insertmacro INSTALLOPTIONS_READ $addToPath "Desktop.ini" "Settings" "AddToPath"
  !insertmacro INSTALLOPTIONS_READ $updateContextMenu "Desktop.ini" "Settings" "UpdateContextMenu"
FunctionEnd


Function ConfirmDesktopShortcut
  !insertmacro MUI_HEADER_TEXT "$(installation_options)" "$(installation_options_prompt)"

  Call getInstallationOptionsPositions

  IntOp $0 $launcherShortcut - 1
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $0" "Text" "$(create_desktop_shortcut)"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $launcherShortcut" "Text" "${MUI_PRODUCT}"
  IntOp $0 $addToPath - 1
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $0" "Text" "$(update_path_var_group)"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $addToPath" "Text" "$(update_path_var_label)"
  IntOp $0 $updateContextMenu - 1
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $0" "Text" "$(update_context_menu_group)"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $updateContextMenu" "Text" "$(update_context_menu_label)"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field ${INSTALL_OPTION_ELEMENTS}" "Text" "$(create_associations_group)"

  Call customPreInstallActions

  StrCmp "${ASSOCIATION}" "NoAssociation" skip_association
  StrCpy $R0 ${INSTALL_OPTION_ELEMENTS}
  ; start position for association checkboxes
  StrCpy $R1 0
  ; space between checkboxes
  StrCpy $R3 5
  ; space for one symbol
  StrCpy $R5 4
  push "${ASSOCIATION}"
loop:
  ; get an association from list of associations
  call SplitStr
  Pop $0
  StrCmp $0 "" done
  ; get length of an association text
  StrLen $R4 $0
  IntOp $R4 $R4 * $R5
  IntOp $R4 $R4 + 20
  ; increase field number
  IntOp $R0 $R0 + 1
  StrCmp $R1 0 first_association 0
  ; calculate  start position for next checkbox of an association using end of previous one.
  IntOp $R1 $R1 + $R3
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $R0" "Left" "$R1"
  Goto calculate_shift
first_association:
  !insertmacro INSTALLOPTIONS_READ $R2 "Desktop.ini" "Field $R0" "Left"
  StrCpy $R1 $R2
calculate_shift:
  IntOp $R1 $R1 + $R4
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $R0" "Right" "$R1"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $R0" "Text" "$0"
  goto loop
skip_association:
  IntOp $R0 ${INSTALL_OPTION_ELEMENTS} - 1
done:
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Settings" "NumFields" "$R0"
  !insertmacro INSTALLOPTIONS_DISPLAY "Desktop.ini"
FunctionEnd


;------------------------------------------------------------------------------
; configuration
;------------------------------------------------------------------------------

BrandingText " "

!define MUI_ABORTWARNING

!insertmacro MUI_PAGE_WELCOME

Page custom uninstallOldVersionDialog

!define MUI_PAGE_CUSTOMFUNCTION_LEAVE OnDirectoryPageLeave
!define MUI_PAGE_HEADER_TEXT "$(choose_install_location)"
!insertmacro MUI_PAGE_DIRECTORY

Page custom ConfirmDesktopShortcut

!define MUI_PAGE_HEADER_TEXT "$(choose_start_menu_folder)"
!define MUI_STARTMENUPAGE_DEFAULTFOLDER "${MANUFACTURER}"
!insertmacro MUI_PAGE_STARTMENU Application $startMenuFolder

!define MUI_PAGE_HEADER_TEXT "$(installing_product)"
!insertmacro MUI_PAGE_INSTFILES

!ifdef RUN_AFTER_FINISH
!define MUI_FINISHPAGE_RUN_CHECKED
!else
!define MUI_FINISHPAGE_RUN_NOTCHECKED
!endif
!define MUI_FINISHPAGE_RUN
!define MUI_FINISHPAGE_RUN_FUNCTION PageFinishRun
!insertmacro MUI_PAGE_FINISH

!define MUI_UNINSTALLER
UninstPage custom un.ConfirmDeleteSettings
!insertmacro MUI_UNPAGE_INSTFILES

Function PageFinishRun
  ${IfNot} ${Silent}
    !insertmacro UAC_AsUser_ExecShell "" "${PRODUCT_EXE_FILE}" "" "$INSTDIR\bin" ""
  ${EndIf}
FunctionEnd

;------------------------------------------------------------------------------
; languages
;------------------------------------------------------------------------------
!insertmacro MUI_LANGUAGE "English"
!insertmacro MUI_LANGUAGE "SimpChinese"
!insertmacro MUI_LANGUAGE "Japanese"
!insertmacro MUI_LANGUAGE "Korean"
!include "idea_en.nsi"
!include "idea_zh_CN.nsi"
!include "idea_ja.nsi"
!include "idea_ko.nsi"


Function .onInstSuccess
  SetErrorLevel 0
  ${LogText} "Installation has been finished successfully."
FunctionEnd


Function silentConfigReader
  ${LogText} ""
  ${LogText} "Silent installation, options"
  ${GetParameters} $R0

  ClearErrors
  ${GetOptions} $R0 /CONFIG= $R1
  ${If} ${Errors}
    ${LogText} "  config file was not provided"
    ${LogText} "  defaulting to admin mode"
    StrCpy $silentMode "admin"
    StrCpy $startMenuFolder "${MANUFACTURER}"
    Return
  ${EndIf}
  ${LogText} "  config file: $R1"

  ClearErrors
  ${ConfigRead} "$R1" "mode=" $R0
  ${If} ${Errors}
    !define msg1 "How to run installation in silent mode:$\n"
    !define msg2 "<installation> /S /CONFIG=<path to silent config with file name> /D=<install dir>$\n$\n"
    !define msg3 "Examples:$\n"
    !define msg4 "Installation.exe /S /CONFIG=d:\download\silent.config /D=d:\JetBrains\Product$\n"
    !define msg5 "Run installation in silent mode with logging:$\n"
    !define msg6 "Installation.exe /S /CONFIG=d:\download\silent.config /LOG=d:\JetBrains\install.log /D=d:\JetBrains\Product$\n"
    MessageBox MB_OK|MB_ICONSTOP "${msg1}${msg2}${msg3}${msg4}${msg5}${msg6}"
    ${LogText} "ERROR: silent installation: incorrect parameters."
    Abort
  ${EndIf}
  ${LogText} "  mode: $R0"
  StrCpy $silentMode $R0

  ClearErrors
  ${ConfigRead} "$R1" "launcher64=" $R3
  ${IfNot} ${Errors}
    ${LogText} "  shortcut for launcher64: $R3"
    !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $launcherShortcut" "State" $R3
  ${EndIf}

  ClearErrors
  ${ConfigRead} "$R1" "updatePATH=" $R3
  ${IfNot} ${Errors}
    ${LogText} "  update PATH env var: $R3"
    !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $addToPath" "Type" "checkbox"
    !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $addToPath" "State" $R3
  ${EndIf}

  ClearErrors
  ${ConfigRead} "$R1" "updateContextMenu=" $R3
  ${IfNot} ${Errors}
    !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $updateContextMenu" "Type" "checkbox"
    !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $updateContextMenu" "State" $R3
  ${EndIf}

  ${If} "${ASSOCIATION}" != "NoAssociation"
    !insertmacro INSTALLOPTIONS_READ $R0 "Desktop.ini" "Settings" "NumFields"
    Push "${ASSOCIATION}"
    ${Do}
      Call SplitStr
      Pop $0
      ${If} $0 == ""
        ${Break}
      ${EndIf}
      ClearErrors
      ${ConfigRead} "$R1" "$0=" $R3
      ${If} ${Errors}
        ${Break}
      ${EndIf}
      IntOp $R0 $R0 + 1
      !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $R0" "State" $R3
      !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $R0" "Text" "$0"
      ${LogText} "  association: $0, state: $R3"
    ${Loop}
    !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Settings" "NumFields" "$R0"
  ${EndIf}
FunctionEnd


Function searchCurrentVersion
  ${LogText} ""
  ${LogText} "Checking if '${MUI_PRODUCT} ${VER_BUILD}' is already installed"

  ReadRegStr $R0 HKCU "Software\${MANUFACTURER}\${PRODUCT_REG_VER}" ""
  ${If} $R0 == ""
  ${OrIfNot} ${FileExists} "$R0\bin\${PRODUCT_EXE_FILE}"
    ReadRegStr $R0 HKLM "Software\${MANUFACTURER}\${PRODUCT_REG_VER}" ""
    ${If} $R0 == ""
    ${OrIfNot} ${FileExists} "$R0\bin\${PRODUCT_EXE_FILE}"
      Return
    ${EndIf}
  ${EndIf}

  MessageBox MB_YESNO|MB_ICONQUESTION "$(current_version_already_installed)" IDYES continue IDNO exit_installer
exit_installer:
  Abort
continue:
FunctionEnd


Function getUninstallOldVersionVars
  !insertmacro INSTALLOPTIONS_READ $max_fields "UninstallOldVersions.ini" "Settings" "NumFields"
  !insertmacro INSTALLOPTIONS_READ $control_fields "UninstallOldVersions.ini" "Settings" "ControlFields"
  !insertmacro INSTALLOPTIONS_READ $bottom_position "UninstallOldVersions.ini" "Settings" "BottomPosition"
  !insertmacro INSTALLOPTIONS_READ $max_length "UninstallOldVersions.ini" "Settings" "MaxLength"
  !insertmacro INSTALLOPTIONS_READ $line_height "UninstallOldVersions.ini" "Settings" "LineHeight"
  !insertmacro INSTALLOPTIONS_READ $extra_space "UninstallOldVersions.ini" "Settings" "ExtraSpace"
FunctionEnd

Function uninstallOldVersionDialog
  StrCpy $R8 $control_fields
  !insertmacro INSTALLOPTIONS_WRITE "UninstallOldVersions.ini" "Field 2" "State" "0"

  StrCpy $R0 0  ; HKLM
  StrCpy $R1 "Software"
  Call enumerateInstalledVersions

  StrCpy $R0 0  ; HKLM
  StrCpy $R1 "Software\Wow6432Node"
  Call enumerateInstalledVersions

  StrCpy $R0 1  ; HKCU
  StrCpy $R1 "Software"
  Call enumerateInstalledVersions

  !insertmacro INSTALLOPTIONS_WRITE "UninstallOldVersions.ini" "Settings" "NumFields" "$R8"
  ${If} $R8 > $control_fields
    !insertmacro MUI_HEADER_TEXT "$(uninstall_previous_installations_title)" ""
    !insertmacro INSTALLOPTIONS_WRITE "UninstallOldVersions.ini" "Field 1" "Text" "$(uninstall_previous_installations_prompt)"
    !insertmacro INSTALLOPTIONS_WRITE "UninstallOldVersions.ini" "Field 2" "Text" "$(uninstall_previous_installations_silent)"
    !insertmacro INSTALLOPTIONS_WRITE "UninstallOldVersions.ini" "Field 3" "Flags" "FOCUS"
    !insertmacro INSTALLOPTIONS_DISPLAY_RETURN "UninstallOldVersions.ini"
    Pop $R9
    ${If} $R9 == "success"
      ; uninstall chosen installation in the chosen mode
      !insertmacro INSTALLOPTIONS_READ $R1 "UninstallOldVersions.ini" "Field 2" "State"
      ${DoWhile} $R8 > $control_fields
        !insertmacro INSTALLOPTIONS_READ $R9 "UninstallOldVersions.ini" "Field $R8" "State"
        ${If} $R9 == "1"
          !insertmacro INSTALLOPTIONS_READ $R0 "UninstallOldVersions.ini" "Field $R8" "Text"
          Call uninstallOldVersion
        ${EndIf}
        IntOp $R8 $R8 - 1
      ${Loop}
    ${EndIf}
  ${EndIf}
FunctionEnd

; $R0 - root key (`0` = HKLM, `1` = HKCU)
; $R1 - subkey
; $R8(in,out) - a counter of fields added to the dialog
Function enumerateInstalledVersions
  StrCpy $R2 0

  ${Do}
    ${If} $R0 = 0
      EnumRegKey $R3 HKLM "$R1\${MANUFACTURER}\${MUI_PRODUCT}" $R2
    ${Else}
      EnumRegKey $R3 HKCU "$R1\${MANUFACTURER}\${MUI_PRODUCT}" $R2
    ${EndIf}
    ${If} $R3 == ""
      ${Break}
    ${EndIf}

    ${If} $R0 = 0
      ReadRegStr $R3 HKLM "$R1\${MANUFACTURER}\${MUI_PRODUCT}\$R3" ""
    ${Else}
      ReadRegStr $R3 HKCU "$R1\${MANUFACTURER}\${MUI_PRODUCT}\$R3" ""
    ${EndIf}
    ${If} $R3 != ""
    ${AndIf} ${FileExists} "$R3\bin\${PRODUCT_EXE_FILE}"
    ${AndIf} ${FileExists} "$R3\bin\Uninstall.exe"
      Call checkProductVersion

      ${If} $R6 != "duplicated"
        IntOp $R8 $R8 + 1
        Call getTopPosition

        IntOp $R7 $R6 + $line_height
        StrLen $R9 $R3
        ${If} $R9 >= $max_length
          IntOp $R7 $R7 + $extra_space
        ${EndIf}
        ${If} $R7 > $bottom_position
          ; the dialog is full
          IntOp $R8 $R8 - 1
          ${Break}
        ${EndIf}

        !insertmacro INSTALLOPTIONS_WRITE "UninstallOldVersions.ini" "Field $R8" "Top" "$R6"
        !insertmacro INSTALLOPTIONS_WRITE "UninstallOldVersions.ini" "Field $R8" "Bottom" "$R7"
        !insertmacro INSTALLOPTIONS_WRITE "UninstallOldVersions.ini" "Field $R8" "State" "0"
        !insertmacro INSTALLOPTIONS_WRITE "UninstallOldVersions.ini" "Field $R8" "Text" "$R3"
      ${EndIf}
    ${EndIf}

    IntOp $R2 $R2 + 1
  ${Loop}
FunctionEnd

; $R3 - a path to the version to be checked
; $R8 - a counter of fields added to the dialog
; $R6(out) - "duplicated" if the path is already added
Function checkProductVersion
  StrCpy $R6 ""
  StrCpy $R7 $control_fields
  IntOp $R7 $R7 + 1
  ${DoWhile} $R7 <= $R8
    !insertmacro INSTALLOPTIONS_READ $R6 "UninstallOldVersions.ini" "Field $R7" "Text"
    ${If} $R6 == $R3
      ; found the same path in the list of installations
      StrCpy $R6 "duplicated"
      Return
    ${EndIf}
    IntOp $R7 $R7 + 1
  ${Loop}
FunctionEnd

; $R8 - a counter of fields added to the dialog
; $R6(out) - the 'Top' position for the new checkbox
Function getTopPosition
  IntOp $R9 $R8 - 1
  ${If} $R9 = $control_fields 
    !insertmacro INSTALLOPTIONS_READ $R6 "UninstallOldVersions.ini" "Field $R8" "Top"
  ${Else}
    !insertmacro INSTALLOPTIONS_READ $R6 "UninstallOldVersions.ini" "Field $R9" "Bottom"
  ${EndIf}
FunctionEnd

; $R0 - a path to the version to uninstall
; $R1 - mode ("1" = silent)
Function uninstallOldVersion
  ${LogText} ""
  ${LogText} "Uninstalling: $R0"

  StrCpy $R9 "$LOCALAPPDATA\${PRODUCT_PATHS_SELECTOR}_${VER_BUILD}_Uninstall.exe"
  CopyFiles "$R0\bin\Uninstall.exe" $R9

  ${If} $R1 == "1"
    ExecWait '"$R9" /S /NO_UNINSTALL_FEEDBACK=true _?=$R0\bin'
  ${Else}
    ExecWait '"$R9" /NO_UNINSTALL_FEEDBACK=true _?=$R0\bin'
  ${EndIf}

  Delete $R9
FunctionEnd


Function ProductRegistration
  ${LogText} ""
  ${LogText} "Do registration ${MUI_PRODUCT} ${VER_BUILD}"
  StrCmp "${PRODUCT_WITH_VER}" "${MUI_PRODUCT} ${VER_BUILD}" eapInfo releaseInfo
eapInfo:
  StrCpy $3 "${PRODUCT_WITH_VER}(EAP)"
  goto createRegistration
releaseInfo:
  StrCpy $3 "${PRODUCT_WITH_VER}"
createRegistration:
  StrCpy $0 "HKCR"
  StrCpy $1 "Applications\${PRODUCT_EXE_FILE}\shell\open"
  StrCpy $2 "FriendlyAppName"
  call OMWriteRegStr
  StrCpy $1 "Applications\${PRODUCT_EXE_FILE}\shell\open\command"
  StrCpy $2 ""
  StrCpy $3 '"$productLauncher" "%1"'
  call OMWriteRegStr
FunctionEnd


Function UpdateContextMenu
  ${LogText} ""
  ${LogText} "Update Context Menu - Open with PRODUCT action for folders"

; add "Open with PRODUCT" action for folders to Windows context menu
  StrCpy $0 "SHCTX"
  StrCpy $1 "Software\Classes\Directory\shell\${MUI_PRODUCT}"
  StrCpy $2 ""
  StrCpy $3 "Open Folder as ${MUI_PRODUCT} Project"
  call OMWriteRegStr

  StrCpy $1 "Software\Classes\Directory\shell\${MUI_PRODUCT}"
  StrCpy $2 "Icon"
  StrCpy $3 "$productLauncher"
  call OMWriteRegStr

  StrCpy $1 "Software\Classes\Directory\shell\${MUI_PRODUCT}\command"
  StrCpy $2 ""
  StrCpy $3 '"$productLauncher" "%1"'
  call OMWriteRegStr

  StrCpy $1 "Software\Classes\Directory\Background\shell\${MUI_PRODUCT}"
  StrCpy $2 ""
  StrCpy $3 "Open Folder as ${MUI_PRODUCT} Project"
  call OMWriteRegStr

  StrCpy $1 "Software\Classes\Directory\Background\shell\${MUI_PRODUCT}"
  StrCpy $2 "Icon"
  StrCpy $3 "$productLauncher"
  call OMWriteRegStr

  StrCpy $1 "Software\Classes\Directory\Background\shell\${MUI_PRODUCT}\command"
  StrCpy $2 ""
  StrCpy $3 '"$productLauncher" "%V"'
  call OMWriteRegStr
FunctionEnd


Function ProductAssociation
  ${LogText} ""
  ${LogText} "Do associations ${MUI_PRODUCT} ${VER_BUILD}"
  push $0
  push $1
  push $2
  push $3
  StrCpy $2 ""
  StrCmp $baseRegKey "HKLM" admin user
admin:
  StrCpy $0 HKCR
  StrCpy $R5 ${PRODUCT_PATHS_SELECTOR}
  goto back_up
user:
  StrCpy $0 HKCU
  StrCpy $R4 "Software\Classes\$R4"
  StrCpy $R5 "Software\Classes\${PRODUCT_PATHS_SELECTOR}"
back_up:
 ; back up old value of an association
  StrCpy $1 $R4
call OMReadRegStr
  StrCmp $3 "" skip_backup
  StrCmp $3 ${PRODUCT_PATHS_SELECTOR} skip_backup
  StrCpy $2 "backup_val"
  Call OMWriteRegStr
skip_backup:
  StrCpy $2 ""
  StrCpy $3 ${PRODUCT_PATHS_SELECTOR}
  Call OMWriteRegStr
  StrCpy $1 $R5
  StrCpy $2 ""
  Call OMReadRegStr
  StrCmp $3 "" 0 command_exists
  StrCpy $2 ""
  StrCpy $3 "${PRODUCT_FULL_NAME}"
  Call OMWriteRegStr
  StrCpy $1 "$R5\shell"
  StrCpy $2 ""
  StrCpy $3 "open"
  Call OMWriteRegStr
  StrCpy $1 "$R5\DefaultIcon"
  StrCpy $2 ""
  StrCpy $3 "$productLauncher,0"
  Call OMWriteRegStr
command_exists:
  StrCpy $1 "$R5\DefaultIcon"
  StrCpy $2 ""
  StrCpy $3 "$productLauncher,0"
  Call OMWriteRegStr
  StrCpy $1 "$R5\shell\open\command"
  StrCpy $2 ""
  StrCpy $3 '"$productLauncher" "%1"'
  Call OMWriteRegStr

  ; add "Edit with PRODUCT" action for files to Windows context menu
  ${LogText} ""
  ${LogText} "Update Context Menu - Edit with PRODUCT"

  StrCpy $0 "SHCTX"
  StrCpy $1 "Software\Classes\*\shell\Open with ${MUI_PRODUCT}"
  StrCpy $2 "Icon"
  StrCpy $3 "$productLauncher"
  call OMWriteRegStr

  StrCpy $1 "Software\Classes\*\shell\Open with ${MUI_PRODUCT}\command"
  StrCpy $2 ""
  StrCpy $3 '"$productLauncher" "%1"'
  call OMWriteRegStr

  StrCpy $1 "Software\Classes\*\shell\Open with ${MUI_PRODUCT}"
  StrCpy $2 ""
  StrCpy $3 "Edit with ${MUI_PRODUCT}"
  call OMWriteRegStr

  pop $3
  pop $2
  pop $1
  pop $0
FunctionEnd


Function updatePathEnvVar
  Var /GLOBAL pathEnvVar

  ClearErrors
  ReadRegStr $pathEnvVar HKCU "Environment" "Path"
  ${If} ${Errors}
    ${LogText} "  ERROR: cannot read the 'Path' env var"
    Return
  ${EndIf}

  ${LogText} "  writing product env var '${MUI_PRODUCT}' = '$INSTDIR\bin'"
  WriteRegStr HKCU "Environment" "${MUI_PRODUCT}" "$INSTDIR\bin"
  ${If} ${Errors}
    ${LogText} "  ERROR: cannot write a product env var"
    Return
  ${EndIf}

  ${StrStr} $R0 $pathEnvVar "%${MUI_PRODUCT}%"
  ${If} $R0 != ""
    ${LogText} "  '${MUI_PRODUCT}' is already on the path"
    Return
  ${EndIf}

  ${If} $pathEnvVar != ""
    StrCpy $R0 $pathEnvVar 1 -1
    ${If} $R0 != ';'
      StrCpy $pathEnvVar "$pathEnvVar;"
    ${EndIf}
  ${EndIf}
  WriteRegExpandStr HKCU "Environment" "Path" "$pathEnvVar%${MUI_PRODUCT}%"
  ${If} ${Errors}
    ${LogText} "  ERROR: cannot write the 'Path' env var"
    Return
  ${EndIf}

  Call postEnvChangeEvent
FunctionEnd


;------------------------------------------------------------------------------
; Installer sections
;------------------------------------------------------------------------------
Section "IDEA Files" CopyIdeaFiles
  CreateDirectory $INSTDIR

  Call customInstallActions

  StrCpy $productLauncher "$INSTDIR\bin\${PRODUCT_EXE_FILE}"
  ${LogText} "Default launcher: $productLauncher"
  DetailPrint "Default launcher: $productLauncher"

  !insertmacro INSTALLOPTIONS_READ $R0 "Desktop.ini" "Field $addToPath" "State"
  ${If} $R0 == 1
    ${LogText} "Updating the 'Path' env var"
    CALL updatePathEnvVar
  ${EndIf}

  ${If} $updateContextMenu > 0
    !insertmacro INSTALLOPTIONS_READ $R0 "Desktop.ini" "Field $updateContextMenu" "State"
    ${If} $R0 == 1
      Call UpdateContextMenu
    ${EndIf}
  ${EndIf}

  !insertmacro INSTALLOPTIONS_READ $R1 "Desktop.ini" "Settings" "NumFields"
  IntCmp $R1 ${INSTALL_OPTION_ELEMENTS} do_association done do_association
do_association:
  StrCpy $R2 ${INSTALL_OPTION_ELEMENTS}
get_user_choice:
  !insertmacro INSTALLOPTIONS_READ $R3 "Desktop.ini" "Field $R2" "State"
  StrCmp $R3 1 "" next_association
  !insertmacro INSTALLOPTIONS_READ $R4 "Desktop.ini" "Field $R2" "Text"
  call ProductAssociation
next_association:
  IntOp $R2 $R2 + 1
  IntCmp $R1 $R2 get_user_choice done get_user_choice
done:
  StrCmp ${IPR} "false" skip_ipr

  ; back up old value of .ipr
  !define Index "Line${__LINE__}"
  ReadRegStr $1 HKCR ".ipr" ""
  StrCmp $1 "" "${Index}-NoBackup"
    StrCmp $1 "IntelliJIdeaProjectFile" "${Index}-NoBackup"
    WriteRegStr HKCR ".ipr" "backup_val" $1
"${Index}-NoBackup:"
  WriteRegStr HKCR ".ipr" "" "IntelliJIdeaProjectFile"
  ReadRegStr $0 HKCR "IntelliJIdeaProjectFile" ""
  StrCmp $0 "" 0 "${Index}-Skip"
	WriteRegStr HKCR "IntelliJIdeaProjectFile" "" "IntelliJ IDEA Project File"
	WriteRegStr HKCR "IntelliJIdeaProjectFile\shell" "" "open"
"${Index}-Skip:"
  WriteRegStr HKCR "IntelliJIdeaProjectFile\DefaultIcon" "" "$productLauncher,0"
  WriteRegStr HKCR "IntelliJIdeaProjectFile\shell\open\command" "" '"$productLauncher" "%1"'
!undef Index

skip_ipr:
  ; readonly section
  ${LogText} ""
  ${LogText} "Copy files to $INSTDIR"
  SectionIn RO

  ; main part
  !include "idea_win.nsh"

  ; registering the application for the "Open With" list
  Call ProductRegistration

  ; setting the working directory for subsequent `CreateShortCut` instructions
  SetOutPath "$INSTDIR"

  ; creating the desktop shortcut
  !insertmacro INSTALLOPTIONS_READ $R0 "Desktop.ini" "Field $launcherShortcut" "State"
  ${If} $R0 == 1
    ${LogText} "Creating shortcut: '$DESKTOP\${INSTALL_DIR_AND_SHORTCUT_NAME}.lnk' -> '$productLauncher'"
    CreateShortCut "$DESKTOP\${INSTALL_DIR_AND_SHORTCUT_NAME}.lnk" "$productLauncher" "" "" "" SW_SHOWNORMAL
  ${EndIf}

  ; creating the start menu shortcut and storing the start menu directory for the uninstaller
  ${If} $startMenuFolder != ""
    !insertmacro MUI_STARTMENU_WRITE_BEGIN Application
    CreateDirectory "$SMPROGRAMS\$startMenuFolder"
    CreateShortCut "$SMPROGRAMS\$startMenuFolder\${INSTALL_DIR_AND_SHORTCUT_NAME}.lnk" "$productLauncher" "" "" "" SW_SHOWNORMAL
    StrCpy $0 $baseRegKey
    StrCpy $1 "Software\${MANUFACTURER}\${PRODUCT_REG_VER}"
    StrCpy $2 "MenuFolder"
    StrCpy $3 "$startMenuFolder"
    Call OMWriteRegStr
    !insertmacro MUI_STARTMENU_WRITE_END
  ${Else}
    DetailPrint "Skipping start menu shortcut."
  ${EndIf}

  ; enabling Java assistive technologies if a screen reader is active (0x0046 = SPI_GETSCREENREADER)
  System::Call 'user32::SystemParametersInfo(i 0x0046, i 0, *i .r1, i 0) i .r0'
  ${LogText} "SystemParametersInfo(SPI_GETSCREENREADER): $0, value=$1"
  ${If} $0 <> 0
  ${AndIf} $1 == 1
    ${If} ${FileExists} "$INSTDIR\jbr\bin\jabswitch.exe"
      ${LogText} "Executing '$\"$INSTDIR\jbr\bin\jabswitch.exe$\" /enable'"
      ExecDos::exec /DETAILED '"$INSTDIR\jbr\bin\jabswitch.exe" /enable' '' ''
      Pop $0
      ${LogText} "Exit code: $0"
    ${EndIf}
    ${If} ${FileExists} "$INSTDIR\jbr\bin\WindowsAccessBridge-64.dll"
    ${AndIfNot} ${FileExists} "$SYSDIR\WindowsAccessBridge-64.dll"
      ${LogText} "Copying '$INSTDIR\jbr\bin\WindowsAccessBridge-64.dll' into '$SYSDIR'"
      ${DisableX64FSRedirection}
      CopyFiles /SILENT "$INSTDIR\jbr\bin\WindowsAccessBridge-64.dll" "$SYSDIR"
      ${EnableX64FSRedirection}
    ${EndIf}
  ${EndIf}

  Call customPostInstallActions

  StrCpy $0 $baseRegKey
  StrCpy $1 "Software\${MANUFACTURER}\${PRODUCT_REG_VER}"
  StrCpy $2 ""
  StrCpy $3 "$INSTDIR"
  Call OMWriteRegStr

  ; writing the uninstaller & creating the uninstall record
  WriteUninstaller "$INSTDIR\bin\Uninstall.exe"
  WriteRegStr SHCTX "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_WITH_VER}" "DisplayName" "${INSTALL_DIR_AND_SHORTCUT_NAME}"
  WriteRegStr SHCTX "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_WITH_VER}" "UninstallString" '"$INSTDIR\bin\Uninstall.exe"'
  WriteRegStr SHCTX "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_WITH_VER}" "QuietUninstallString" '"$INSTDIR\bin\Uninstall.exe" /S'
  WriteRegStr SHCTX "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_WITH_VER}" "InstallLocation" "$INSTDIR"
  WriteRegStr SHCTX "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_WITH_VER}" "DisplayIcon" "$productLauncher"
  WriteRegStr SHCTX "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_WITH_VER}" "DisplayVersion" "${VER_BUILD}"
  WriteRegStr SHCTX "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_WITH_VER}" "Publisher" "JetBrains s.r.o."
  WriteRegStr SHCTX "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_WITH_VER}" "URLInfoAbout" "https://www.jetbrains.com/products"
  WriteRegStr SHCTX "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_WITH_VER}" "InstallType" "$baseRegKey"
  WriteRegDWORD SHCTX "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_WITH_VER}" "NoModify" 1
  WriteRegDWORD SHCTX "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_WITH_VER}" "NoRepair" 1

  ; reset icon cache
  ${LogText} "Reset icon cache"
  System::Call 'shell32::SHChangeNotify(i 0x08000000, i 0, i 0, i 0) v'
SectionEnd


Function .onInit
  SetRegView 64
  Call createLog

  ${GetNativeMachineArchitecture} $R0
  ${IfNot} $R0 == ${INSTALLER_ARCH}
  ${OrIfNot} ${AtLeastBuild} 14393  ; Windows 10 1607 / Windows Server 2016
    ${LogText} "Architecture: expected=${INSTALLER_ARCH} actual=$R0"
    ReadEnvStr $R0 "TEAMCITY_VERSION"
    ${If} $R0 == ""
      MessageBox MB_OK "$(unsupported_win_version)"
      Abort
    ${Else}
      ${LogText} "  ... ignored on TeamCity"
    ${EndIf}
  ${EndIf}

  ${IfNot} ${Silent}
    Call searchCurrentVersion
  ${EndIf}

  !insertmacro INSTALLOPTIONS_EXTRACT "Desktop.ini"
  Call getInstallationOptionsPositions
  !insertmacro INSTALLOPTIONS_EXTRACT "UninstallOldVersions.ini"
  Call getUninstallOldVersionVars

  SetShellVarContext current
  StrCpy $baseRegKey "HKCU"

  IfSilent silent_mode uac_elevate
silent_mode:
  Call checkAvailableDiskSpace

  ${If} ${CUSTOM_SILENT_CONFIG} = 0
    Call silentConfigReader
  ${Else}
    Call customSilentConfigReader
  ${EndIf}

  StrCmp $silentMode "admin" uac_elevate check_install_dir

uac_elevate:
  !insertmacro UAC_RunElevated
  StrCmp 1223 $0 uac_elevation_aborted ; UAC dialog aborted by user? - continue install under user
  StrCmp 0 $0 0 uac_err ; Error?
  StrCmp 1 $1 0 uac_success ;Are we the real deal or just the wrapper?
  Quit
uac_err:
  Abort
uac_elevation_aborted:
  ${LogText} ""
  ${LogText} "  NOTE: UAC elevation has been aborted. Installation dir might be changed."
  ${LogText} ""
  goto check_install_dir
uac_success:
  StrCmp 1 $3 uac_admin ;Admin?
  StrCmp 3 $1 0 uac_elevation_aborted ;Try again?
  goto uac_elevate
uac_admin:
  SetShellVarContext all
  StrCpy $baseRegKey "HKLM"

check_install_dir:
  ${If} $baseRegKey == "HKCU"
  ${AndIf} "$INSTDIR" == "${DEFAULT_INST_DIR}"
    StrCpy $INSTDIR "$LOCALAPPDATA\Programs\${INSTALL_DIR_AND_SHORTCUT_NAME}"
  ${EndIf}
  ${If} ${Silent}
    Call OnDirectoryPageLeave ; in the silent mode, check if the installation folder is not empty
  ${EndIf}
  ${LogText} "Root registry key: $baseRegKey"
  ${LogText} "Installation dir: $INSTDIR"

  ${IfNot} ${Silent}
    Call adjustLanguage
    ;!insertmacro MUI_LANGDLL_DISPLAY
  ${EndIf}
FunctionEnd


Function checkAvailableDiskSpace
  StrCpy $0 $INSTDIR 3  ; copying first 3 characters
  Call getFreeDiskSpace
  ${LogText} "Space available: $1 MiB"
  ${If} $1 > 0
    SectionGetSize ${CopyIdeaFiles} $R0
    System::Int64Op $R0 >>> 10  ; converting KiBs to MiBs
    Pop $R0
    ${LogText} "Space required: $R0 MiB"
    System::Int64Op $1 > $R0
    Pop $R1
    ${If} $R1 < 1
      ${LogText} "ERROR: Not enough disk space!"
      MessageBox MB_OK|MB_ICONSTOP "$(out_of_disk_space)"
      Abort
    ${EndIf}
  ${EndIf}
FunctionEnd

; returns the amount of free space on a disk $0, in MiBs, in $1
Function getFreeDiskSpace
  System::Call 'kernel32::GetDiskFreeSpaceEx(t "$0", *l .r1, *l .r2, *l .r3) i .r0'
  ${If} $0 <> 0
    System::Int64Op $1 >>> 20  ; converting bytes to MiBs
    Pop $1
  ${Else}
    System::Call 'kernel32::GetLastError() i .r0'
    ${LogText} "GetDiskFreeSpaceEx: $0"
    StrCpy $1 -1
  ${EndIf}
FunctionEnd

;------------------------------------------------------------------------------
; custom uninstall functions
;------------------------------------------------------------------------------

Function un.getRegKey
  ReadRegStr $R2 HKCU "Software\${MANUFACTURER}\${PRODUCT_REG_VER}" ""
  ${If} "$R2\bin" == $INSTDIR
    StrCpy $baseRegKey "HKCU"
  ${Else}
    ReadRegStr $R2 HKLM "Software\${MANUFACTURER}\${PRODUCT_REG_VER}" ""
    ${If} "$R2\bin" == $INSTDIR
      StrCpy $baseRegKey "HKLM"
    ${Else}
      ; registry key is missing; compare $INSTDIR with default user locations
      ${UnStrStr} $R0 $INSTDIR "$LOCALAPPDATA\${MANUFACTURER}"
      ${UnStrStr} $R1 $INSTDIR "$LOCALAPPDATA\Programs"
      ${If} $R0 == $INSTDIR
      ${OrIf} $R1 == $INSTDIR
        StrCpy $baseRegKey "HKCU"
      ${Else}
        StrCpy $baseRegKey "HKLM"  ; undefined location
      ${EndIf}
    ${EndIf}
  ${EndIf}
FunctionEnd


Function un.onUninstSuccess
  SetErrorLevel 0
FunctionEnd


; do not ask user for uninstall feedback if started from another installation
Function un.UninstallFeedback
  ${GetParameters} $R0
  ClearErrors
  ${GetOptions} $R0 /NO_UNINSTALL_FEEDBACK= $R1
  ${IfNot} ${Errors}
    !insertmacro INSTALLOPTIONS_WRITE "DeleteSettings.ini" "Field 6" "State" "0"
  ${EndIf}
FunctionEnd


Function un.onInit
  SetRegView 64

  ; checking that the uninstaller is in the expected location ("...\bin" subdirectory)
  ${IfNot} ${FileExists} "$INSTDIR\fsnotifier.exe"
  ${OrIfNot} ${FileExists} "$INSTDIR\${PRODUCT_EXE_FILE}"
    MessageBox MB_OK|MB_ICONEXCLAMATION "$(uninstaller_relocated)"
    Abort
  ${EndIf}

  Call un.getRegKey
  ${If} $baseRegKey == "HKLM"
    ; checking that the uninstaller is running from the product location
    IfFileExists $LOCALAPPDATA\${PRODUCT_PATHS_SELECTOR}_${VER_BUILD}_Uninstall.exe UAC_Elevate required_admin_perm

  required_admin_perm:
    ; does the user have admin rights?
    UserInfo::GetAccountType
    Pop $R2
    StrCmp $R2 "Admin" UAC_Admin

    StrCpy $R0 "$LOCALAPPDATA\${PRODUCT_PATHS_SELECTOR}_${VER_BUILD}_Uninstall.exe"
    CopyFiles "$OUTDIR\Uninstall.exe" "$R0"
    ${If} ${Silent}
      ExecWait '$R0 /S _?=$INSTDIR'
    ${Else}
      ExecWait '$R0 _?=$INSTDIR'
    ${EndIf}
    Delete "$R0"
    RMDir "$INSTDIR\bin"
    RMDir "$INSTDIR"
    Quit

  UAC_Elevate:
    !insertmacro UAC_RunElevated
    StrCmp 1223 $0 UAC_ElevationAborted ; UAC dialog aborted by user? - continue install under user
    StrCmp 0 $0 0 UAC_Err ; Error?
    StrCmp 1 $1 0 UAC_Success ;Are we the real deal or just the wrapper?
    Quit
  UAC_ElevationAborted:
  UAC_Err:
    Abort
  UAC_Success:
    StrCmp 1 $3 UAC_Admin ;Admin?
    StrCmp 3 $1 0 UAC_ElevationAborted ;Try again?
    goto UAC_Elevate
  UAC_Admin:
    SetShellVarContext all
    StrCpy $baseRegKey "HKLM"
  ${EndIf}

  ${IfNot} ${Silent}
    Call un.adjustLanguage
    ;!insertmacro MUI_UNGETLANGUAGE
  ${EndIf}

  !insertmacro INSTALLOPTIONS_EXTRACT "DeleteSettings.ini"
  Call un.UninstallFeedback
FunctionEnd


Function un.RestoreBackupRegValue
  ;replace Default str with the backup value (if there is the one) and then delete backup
  ; $1 - key (for example ".java")
  ; $2 - name (for example "backup_val")
  Push $0
  Push $3

  StrCmp $baseRegKey "HKLM" admin user
admin:
  StrCpy $0 HKCR
  goto read_backup_value
user:
  StrCpy $0 HKCU
  StrCpy $1 "Software\Classes\$1"

read_backup_value:
  call un.OMReadRegStr
  StrCmp $3 "" no_backup restore_backup

no_backup:
  ;clean default value if it contains current product info
  StrCpy $2 ""
  call un.OMReadRegStr
  StrCmp $4 $3 0 done
  call un.OMDeleteRegValue
  goto done

restore_backup:
  StrCmp $3 $4 remove_backup 0
  push $2
  StrCpy $2 ""
  call un.OMWriteRegStr
  pop $2
remove_backup:
  call un.OMDeleteRegValue

done:
  Pop $3
  Pop $0
FunctionEnd


Function un.PSEnum
  ${If} $2 == "$INSTDIR\bin\${PRODUCT_EXE_FILE}"
  ${OrIf} $2 == "$INSTDIR\jbr\bin\java.exe"
    StrCpy $R1 "[$0] $2"
    DetailPrint "$R1"
    StrCpy $0 ""
  ${EndIf}
FunctionEnd

Function un.checkIfIDEIsRunning
  GetFunctionAddress $R0 un.PSEnum
check_processes:
  DetailPrint "Enumerating processes"
  StrCpy $R1 ""
  PS::Enum $R0
  ${If} $R1 == ""
    Return
  ${EndIf}
  MessageBox MB_OKCANCEL|MB_ICONQUESTION|MB_TOPMOST "$(application_running)" IDOK check_processes
  Abort
FunctionEnd


Function un.deleteDirectoryWithParent
  RMDir /R "$0"
  RMDir "$0\.."  ; delete a parent directory if empty
FunctionEnd


Function un.deleteShortcutIfRight
  ${IfNot} ${FileExists} "$0"
    DetailPrint "The $1 shortcut '$0' does does not exist."
    Return
  ${EndIf}

  ClearErrors
  ShellLink::GetShortCutTarget "$0"
  Pop $R1
  ${IfNot} ${Errors}
  ${AndIf} $R1 == "$INSTDIR\bin\${PRODUCT_EXE_FILE}"
    DetailPrint "Deleting the $1 shortcut: $0"
    Delete "$0"
    ${If} $1 == "start menu"
      RMDir "$0\.."  ; delete the parent group if empty
    ${EndIf}
  ${Else}
    DetailPrint "The link '$0' does does not point to a valid launcher."
  ${EndIf}
FunctionEnd


;------------------------------------------------------------------------------
; custom uninstall pages
;------------------------------------------------------------------------------

Function un.ConfirmDeleteSettings
  !insertmacro MUI_HEADER_TEXT "$(uninstall_options)" ""

  ${GetParent} $INSTDIR $R1
  ${UnStrRep} $R1 $R1 '\' '\\'
  !insertmacro INSTALLOPTIONS_WRITE "DeleteSettings.ini" "Field 1" "Text" "$(prompt_delete_settings)"
  !insertmacro INSTALLOPTIONS_WRITE "DeleteSettings.ini" "Field 2" "Text" $R1
  !insertmacro INSTALLOPTIONS_WRITE "DeleteSettings.ini" "Field 3" "Text" "$(text_delete_settings)"
  !insertmacro INSTALLOPTIONS_WRITE "DeleteSettings.ini" "Field 4" "Text" "$(confirm_delete_caches)"
  !insertmacro INSTALLOPTIONS_WRITE "DeleteSettings.ini" "Field 5" "Text" "$(confirm_delete_settings)"

  ${UnStrStr} $R0 "${MUI_PRODUCT}" "JetBrains Rider"
  ${If} $R0 == "${MUI_PRODUCT}"
    !insertmacro INSTALLOPTIONS_WRITE "DeleteSettings.ini" "Field 7" "Text" "$(confirm_delete_rider_build_tools)"
  ${Else}
    !insertmacro INSTALLOPTIONS_WRITE "DeleteSettings.ini" "Field 7" "Type" "Label"
    !insertmacro INSTALLOPTIONS_WRITE "DeleteSettings.ini" "Field 7" "Text" ""
  ${EndIf}

  ${If} "${UNINSTALL_WEB_PAGE}" != ""
    !insertmacro INSTALLOPTIONS_WRITE "DeleteSettings.ini" "Field 6" "Text" "$(share_uninstall_feedback)"
  ${Else}
    !insertmacro INSTALLOPTIONS_WRITE "DeleteSettings.ini" "Field 6" "Type" "Label"
    !insertmacro INSTALLOPTIONS_WRITE "DeleteSettings.ini" "Field 6" "Text" ""
  ${EndIf}

  !insertmacro INSTALLOPTIONS_DISPLAY "DeleteSettings.ini"
FunctionEnd


Section "Uninstall"
  DetailPrint "Root registry key: $baseRegKey"

  ; the uninstaller is in the "...\bin" subdirectory; correcting
  ${GetParent} "$INSTDIR" $INSTDIR
  DetailPrint "Uninstalling from: $INSTDIR"

  Call un.checkIfIDEIsRunning

  Call un.customUninstallActions

  ; deleting the start menu shortcut
  StrCpy $0 $baseRegKey
  StrCpy $1 "Software\${MANUFACTURER}\${PRODUCT_REG_VER}"
  StrCpy $2 "MenuFolder"
  Call un.OMReadRegStr
  ${If} $3 != ""
    StrCpy $0 "$SMPROGRAMS\$3\${INSTALL_DIR_AND_SHORTCUT_NAME}.lnk"
    StrCpy $1 "start menu"
    Call un.deleteShortcutIfRight
  ${EndIf}

  ; deleting the desktop shortcut
  StrCpy $0 "$DESKTOP\${INSTALL_DIR_AND_SHORTCUT_NAME}.lnk"
  StrCpy $1 "desktop"
  Call un.deleteShortcutIfRight

  ; deleting the 'Path' record
  ReadRegStr $R0 HKCU "Environment" "${MUI_PRODUCT}"
  ${If} $R0 == "$INSTDIR\bin"
    ReadRegStr $R1 HKCU "Environment" "Path"
    ${UnStrRep} $R2 $R1 ";%${MUI_PRODUCT}%" ""
    ${If} $R2 != $R1
    ${AndIf} $R2 != ""
      DetailPrint "Updating the 'Path' environment variable."
      WriteRegExpandStr HKCU "Environment" "Path" "$R2"
    ${EndIf}
    DetailPrint "Deleting the '${MUI_PRODUCT}' environment variable."
    DeleteRegValue HKCU "Environment" "${MUI_PRODUCT}"
    Call un.postEnvChangeEvent
  ${EndIf}

  ; setting the context for `$APPDATA` and `$LOCALAPPDATA`
  ${If} $baseRegKey == "HKLM"
    SetShellVarContext current
  ${EndIf}

  ; deleting caches
  !insertmacro INSTALLOPTIONS_READ $R2 "DeleteSettings.ini" "Field 4" "State"
  ${If} $R2 == 1
    StrCpy $0 "$LOCALAPPDATA\${MANUFACTURER}\${PRODUCT_PATHS_SELECTOR}"
    DetailPrint "Deleting caches: $0"
    Call un.deleteDirectoryWithParent
  ${Else}
    DetailPrint "Keeping caches"
  ${EndIf}

  ; deleting settings
  !insertmacro INSTALLOPTIONS_READ $R2 "DeleteSettings.ini" "Field 5" "State"
  ${If} $R2 == 1
    StrCpy $0 "$APPDATA\${MANUFACTURER}\${PRODUCT_PATHS_SELECTOR}"
    DetailPrint "Deleting settings: $0"
    Call un.deleteDirectoryWithParent
  ${Else}
    DetailPrint "Keeping settings"
  ${EndIf}

  ; restoring the context
  ${If} $baseRegKey == "HKLM"
    SetShellVarContext all
  ${EndIf}

  ; deleting the uninstaller itself and other cruft
  Delete "$INSTDIR\bin\Uninstall.exe"
  Delete "$INSTDIR\jbr\bin\server\classes.jsa"

  ; main part
  !include "un_idea_win.nsh"
  RMDir "$INSTDIR\bin"
  RMDir "$INSTDIR"

  ; removing the directory context menu action
  StrCpy $0 "SHCTX"
  StrCpy $1 "Software\Classes\*\shell\Open with ${MUI_PRODUCT}"
  Call un.OMDeleteRegKey
  StrCpy $1 "Software\Classes\Directory\shell\${MUI_PRODUCT}"
  Call un.OMDeleteRegKey
  StrCpy $1 "Software\Classes\Directory\Background\shell\${MUI_PRODUCT}"
  Call un.OMDeleteRegKey

  ; restoring file associations
  StrCpy $5 "Software\${MANUFACTURER}"
  StrCmp "${ASSOCIATION}" "NoAssociation" finish_uninstall
  push "${ASSOCIATION}"
loop:
  StrCpy $2 "backup_val"
  StrCpy $4 "${PRODUCT_PATHS_SELECTOR}"
  call un.SplitStr
  Pop $0
  StrCmp $0 "" finish_uninstall

  ;restore backup association(s)
  StrCpy $1 $0
  Call un.RestoreBackupRegValue
  goto loop

finish_uninstall:
  StrCpy $0 $baseRegKey
  StrCpy $1 "$5\${PRODUCT_REG_VER}"
  StrCpy $4 0

getValue:
  Call un.OMEnumRegValue
  IfErrors finish delValue
delValue:
  StrCpy $2 $3
  Call un.OMDeleteRegValue
  IfErrors 0 +2
  IntOp $4 $4 + 1
  goto getValue

finish:
  StrCpy $1 "$5\${PRODUCT_REG_VER}"
  Call un.OMDeleteRegKeyIfEmpty
  StrCpy $1 "$5"
  Call un.OMDeleteRegKeyIfEmpty

  StrCpy $0 "HKCR"
  StrCpy $1 "Applications\${PRODUCT_EXE_FILE}"
  Call un.OMDeleteRegKey

  StrCpy $0 $baseRegKey
  StrCmp $baseRegKey "HKLM" admin user
admin:
  StrCpy $1 "${PRODUCT_PATHS_SELECTOR}"
  goto delete_association
user:
  StrCpy $1 "Software\Classes\${PRODUCT_PATHS_SELECTOR}"
delete_association:
  ; remove product information which was used for association(s)
  Call un.OMDeleteRegKey

  ; dropping the .ipr association
  StrCpy $0 "HKCR"
  StrCpy $1 "IntelliJIdeaProjectFile\DefaultIcon"
  StrCpy $2 ""
  Call un.OMReadRegStr
  ${If} $3 == "$INSTDIR\bin\${PRODUCT_EXE_FILE},0"
    StrCpy $1 "IntelliJIdeaProjectFile"
    Call un.OMDeleteRegKey
  ${EndIf}

  ; deleting the uninstall record
  StrCpy $0 $baseRegKey
  StrCpy $1 "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_WITH_VER}"
  Call un.OMDeleteRegKey

  ; opening the uninstall feedback page
  ${IfNot} ${Silent}
  ${AndIfNot} "${UNINSTALL_WEB_PAGE}" == ""
    !insertmacro INSTALLOPTIONS_READ $R2 "DeleteSettings.ini" "Field 6" "State"
    ${If} $R2 == 1
      ExecShell "" "${UNINSTALL_WEB_PAGE}"
    ${EndIf}
  ${EndIf}
SectionEnd
