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
${StrTok}
${UnStrRep}
${UnStrStr}

!include "log.nsi"
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

Var startMenuFolder
Var productLauncher
Var rootRegKey
Var productRegKey
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

  ${If} "${ASSOCIATION}" == "NoAssociation"
    IntOp $R0 ${INSTALL_OPTION_ELEMENTS} - 1
  ${Else}
    StrCpy $R0 ${INSTALL_OPTION_ELEMENTS}
    StrCpy $R1 0  ; start position for association checkboxes
    StrCpy $R3 5  ; space between checkboxes
    StrCpy $R5 4  ; space for one symbol

    StrCpy $9 0
    ${Do}
      ${StrTok} $0 "${ASSOCIATION}" "," $9 1
      ${If} $0 == ""
        ${Break}
      ${EndIf}
      ; get length of an association text
      StrLen $R4 $0
      IntOp $R4 $R4 * $R5
      IntOp $R4 $R4 + 20
      ; calculate the start position for next checkbox of an association using end of previous one
      IntOp $R0 $R0 + 1
      ${If} $R1 = 0
        !insertmacro INSTALLOPTIONS_READ $R2 "Desktop.ini" "Field $R0" "Left"
        StrCpy $R1 $R2
      ${Else}
        IntOp $R1 $R1 + $R3
        !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $R0" "Left" "$R1"
      ${EndIf}
      IntOp $R1 $R1 + $R4
      !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $R0" "Right" "$R1"
      !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $R0" "Text" "$0"

      IntOp $9 $9 + 1
    ${Loop}
  ${EndIf}
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

  StrCpy $startMenuFolder "${MANUFACTURER}"

  ClearErrors
  ${GetOptions} $R0 /CONFIG= $R1
  ${If} ${Errors}
    ${LogText} "  config file was not provided"
    ${LogText} "  defaulting to admin mode"
    StrCpy $silentMode "admin"
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
    StrCpy $9 0
    ${Do}
      ${StrTok} $0 "${ASSOCIATION}" "," $9 1
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
      IntOp $9 $9 + 1
    ${Loop}
    !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Settings" "NumFields" "$R0"
  ${EndIf}
FunctionEnd


Function searchCurrentVersion
  ${LogText} ""
  ${LogText} "Checking if '${MUI_PRODUCT} ${VER_BUILD}' is already installed"

  ReadRegStr $R0 HKCU "Software\${MANUFACTURER}\${MUI_PRODUCT}\${VER_BUILD}" ""
  ${If} $R0 == ""
  ${OrIfNot} ${FileExists} "$R0\bin\${PRODUCT_EXE_FILE}"
    ReadRegStr $R0 HKLM "Software\${MANUFACTURER}\${MUI_PRODUCT}\${VER_BUILD}" ""
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
  StrCpy $R2 "${MUI_PRODUCT}"
  Call enumerateInstalledVersions

  StrCpy $R0 0  ; HKLM
  StrCpy $R1 "Software\Wow6432Node"
  StrCpy $R2 "${MUI_PRODUCT}"
  Call enumerateInstalledVersions

  StrCpy $R0 1  ; HKCU
  StrCpy $R1 "Software"
  StrCpy $R2 "${MUI_PRODUCT}"
  Call enumerateInstalledVersions

  ${If} "${MUI_PRODUCT_ALT}" != ""
    StrCpy $R0 0  ; HKLM
    StrCpy $R1 "Software"
    StrCpy $R2 "${MUI_PRODUCT_ALT}"
    Call enumerateInstalledVersions

    StrCpy $R0 0  ; HKLM
    StrCpy $R1 "Software\Wow6432Node"
    StrCpy $R2 "${MUI_PRODUCT_ALT}"
    Call enumerateInstalledVersions

    StrCpy $R0 1  ; HKCU
    StrCpy $R1 "Software"
    StrCpy $R2 "${MUI_PRODUCT_ALT}"
    Call enumerateInstalledVersions
  ${EndIf}

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
; $R2 - product name
; $R8(in,out) - a counter of fields added to the dialog
Function enumerateInstalledVersions
  StrCpy $R4 0

  ${Do}
    ${If} $R0 = 0
      EnumRegKey $R3 HKLM "$R1\${MANUFACTURER}\$R2" $R4
    ${Else}
      EnumRegKey $R3 HKCU "$R1\${MANUFACTURER}\$R2" $R4
    ${EndIf}
    ${If} $R3 == ""
      ${Break}
    ${EndIf}

    ${If} $R0 = 0
      ReadRegStr $R3 HKLM "$R1\${MANUFACTURER}\$R2\$R3" ""
    ${Else}
      ReadRegStr $R3 HKCU "$R1\${MANUFACTURER}\$R2\$R3" ""
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

    IntOp $R4 $R4 + 1
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


Section "IDEA Files" CopyIdeaFiles
  CreateDirectory $INSTDIR

  Call customInstallActions

  StrCpy $productLauncher "$INSTDIR\bin\${PRODUCT_EXE_FILE}"
  ${LogText} "Default launcher: $productLauncher"
  DetailPrint "Default launcher: $productLauncher"

  ; main part (read-only section)
  ${LogText} ""
  ${LogText} "Copy files to $INSTDIR"
  SectionIn RO
  !include "idea_win.nsh"

  WriteUninstaller "$INSTDIR\bin\Uninstall.exe"
  Call UninstallRecord

  Call UpdateContextMenu
  Call ProductAssociation
  Call ProductRegistration
  Call UpdatePathEnvVar
  Call StartMenuShortcut
  Call DesktopShortcut
  Call JavaAssist

  Call customPostInstallActions

  ${RefreshShellIcons}
SectionEnd

Function UninstallRecord
  WriteRegStr SHCTX "Software\${MANUFACTURER}\${MUI_PRODUCT}\${VER_BUILD}" "" "$INSTDIR"
  ${If} $startMenuFolder != ""
    WriteRegStr SHCTX "Software\${MANUFACTURER}\${MUI_PRODUCT}\${VER_BUILD}" "MenuFolder" "$startMenuFolder"
  ${EndIf}
  WriteRegStr SHCTX "Software\${MANUFACTURER}\${MUI_PRODUCT}\${VER_BUILD}" "AssociationKey" "${PRODUCT_PATHS_SELECTOR}"

  StrCpy $0 "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_WITH_VER}"
  WriteRegStr SHCTX $0 "DisplayName" "${INSTALL_DIR_AND_SHORTCUT_NAME}"
  WriteRegStr SHCTX $0 "UninstallString" '"$INSTDIR\bin\Uninstall.exe"'
  WriteRegStr SHCTX $0 "QuietUninstallString" '"$INSTDIR\bin\Uninstall.exe" /S'
  WriteRegStr SHCTX $0 "InstallLocation" "$INSTDIR"
  WriteRegStr SHCTX $0 "DisplayIcon" "$productLauncher"
  WriteRegStr SHCTX $0 "DisplayVersion" "${VER_BUILD}"
  WriteRegStr SHCTX $0 "Publisher" "JetBrains s.r.o."
  WriteRegStr SHCTX $0 "URLInfoAbout" "https://www.jetbrains.com/products"
  WriteRegDWORD SHCTX $0 "NoModify" 1
  WriteRegDWORD SHCTX $0 "NoRepair" 1
FunctionEnd

Function UpdateContextMenu
  ${If} $updateContextMenu > 0
    !insertmacro INSTALLOPTIONS_READ $R0 "Desktop.ini" "Field $updateContextMenu" "State"
    ${If} $R0 == 1
      ${LogText} ""
      ${LogText} "Update Context Menu - 'Open with ...' action for folders"
      DetailPrint "Update Context Menu - 'Open with ...' action for folders"

      StrCpy $0 "Software\Classes\Directory\shell\${MUI_PRODUCT}"
      WriteRegStr SHCTX $0 "" "Open Folder as ${MUI_PRODUCT} Project"
      WriteRegStr SHCTX $0 "Icon" "$productLauncher"
      WriteRegStr SHCTX "$0\command" "" '"$productLauncher" "%1"'

      StrCpy $0 "Software\Classes\Directory\Background\shell\${MUI_PRODUCT}"
      WriteRegStr SHCTX $0 "" "Open Folder as ${MUI_PRODUCT} Project"
      WriteRegStr SHCTX $0 "Icon" "$productLauncher"
      WriteRegStr SHCTX "$0\command" "" '"$productLauncher" "%V"'

      ${LogText} "Update Context Menu - 'Edit with ...' action for files"
      DetailPrint "Update Context Menu - 'Edit with ...' action for files"

      StrCpy $0 "Software\Classes\*\shell\Open with ${MUI_PRODUCT}"
      WriteRegStr SHCTX $0 "" "Edit with ${MUI_PRODUCT}"
      WriteRegStr SHCTX $0 "Icon" "$productLauncher"
      WriteRegStr SHCTX "$0\command" "" '"$productLauncher" "%1"'
    ${EndIf}
  ${EndIf}
FunctionEnd

Function ProductAssociation
  ${LogText} ""
  ${LogText} "Setting up file associations"

  !insertmacro INSTALLOPTIONS_READ $R9 "Desktop.ini" "Settings" "NumFields"
  ${If} $R9 > ${INSTALL_OPTION_ELEMENTS}
    StrCpy $R8 ${INSTALL_OPTION_ELEMENTS}
    StrCpy $R1 "${PRODUCT_PATHS_SELECTOR}"
    StrCpy $R2 "${PRODUCT_FULL_NAME}"
    ${DoWhile} $R8 < $R9
      !insertmacro INSTALLOPTIONS_READ $R3 "Desktop.ini" "Field $R8" "State"
      ${If} $R3 == 1
        !insertmacro INSTALLOPTIONS_READ $R0 "Desktop.ini" "Field $R8" "Text"
        Call DoProductAssociation
      ${EndIf}
      IntOp $R8 $R8 + 1
    ${Loop}
  ${EndIf}

  ${If} ${IPR} == "true"
    StrCpy $R0 ".ipr"
    StrCpy $R1 "IntelliJIdeaProjectFile"
    StrCpy $R2 "IntelliJ IDEA Project File"
    Call DoProductAssociation
  ${EndIf}
FunctionEnd

; $R0 - extension
; $R1 - association key
; $R2 - association display name
Function DoProductAssociation
  ${LogText} "  associating '$R2' ($R1) with $R0 files"
  DetailPrint "Associating '$R2' with $R0 files"

  StrCpy $0 "Software\Classes\$R0"
  StrCpy $1 "Software\Classes\$R1"

  ReadRegStr $2 SHCTX $0 ""
  ${If} $2 != ""
  ${AndIf} $2 != $R1
    WriteRegStr SHCTX $0 "backup_val" $2
  ${EndIf}
  WriteRegStr SHCTX $0 "" $R1

  WriteRegStr SHCTX $1 "" $R2
  WriteRegStr SHCTX "$1\DefaultIcon" "" "$productLauncher,0"
  WriteRegStr SHCTX "$1\shell" "" "open"
  WriteRegStr SHCTX "$1\shell\open\command" "" '"$productLauncher" "%1"'
FunctionEnd

Function ProductRegistration
  ${If} "${PRODUCT_WITH_VER}" == "${MUI_PRODUCT} ${VER_BUILD}"
    StrCpy $3 "${PRODUCT_WITH_VER}(EAP)"
  ${Else}
    StrCpy $3 "${PRODUCT_WITH_VER}"
  ${EndIf}

  ${LogText} ""
  ${LogText} "Registering '$3' in the application list"
  DetailPrint "Registering '$3' in the application list"

  WriteRegStr SHCTX "Software\Classes\Applications\${PRODUCT_EXE_FILE}\shell\open" "FriendlyAppName" "$3"
  WriteRegStr SHCTX "Software\Classes\Applications\${PRODUCT_EXE_FILE}\shell\open\command" "" '"$productLauncher" "%1"'
FunctionEnd

Function UpdatePathEnvVar
  Var /GLOBAL pathEnvVar

  !insertmacro INSTALLOPTIONS_READ $0 "Desktop.ini" "Field $addToPath" "State"
  ${If} $0 != 1
    Return
  ${EndIf}

  ${LogText} ""
  ${LogText} "Updating the 'Path' env var"
  DetailPrint "Updating the 'Path' env var"

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

  ${StrStr} $0 $pathEnvVar "%${MUI_PRODUCT}%"
  ${If} $0 != ""
    ${LogText} "  '${MUI_PRODUCT}' is already on the path"
    Return
  ${EndIf}

  ${If} $pathEnvVar != ""
    StrCpy $0 $pathEnvVar 1 -1
    ${If} $0 != ';'
      StrCpy $pathEnvVar "$pathEnvVar;"
    ${EndIf}
  ${EndIf}
  WriteRegExpandStr HKCU "Environment" "Path" "$pathEnvVar%${MUI_PRODUCT}%"
  ${If} ${Errors}
    ${LogText} "  ERROR: cannot write the 'Path' env var"
    Return
  ${EndIf}

  Call PostEnvChangeEvent
FunctionEnd

Function StartMenuShortcut
  ${If} $startMenuFolder != ""
    SetOutPath "$INSTDIR"  ; shortcut's working directory
    ${LogText} "Creating shortcut: '$SMPROGRAMS\$startMenuFolder\${INSTALL_DIR_AND_SHORTCUT_NAME}.lnk'"
    !insertmacro MUI_STARTMENU_WRITE_BEGIN Application
    CreateDirectory "$SMPROGRAMS\$startMenuFolder"
    CreateShortCut "$SMPROGRAMS\$startMenuFolder\${INSTALL_DIR_AND_SHORTCUT_NAME}.lnk" "$productLauncher" "" "" "" SW_SHOWNORMAL
    !insertmacro MUI_STARTMENU_WRITE_END
  ${Else}
    DetailPrint "Skipping start menu shortcut."
    ${LogText} "Skipping start menu shortcut."
  ${EndIf}
FunctionEnd

Function DesktopShortcut
  !insertmacro INSTALLOPTIONS_READ $R0 "Desktop.ini" "Field $launcherShortcut" "State"
  ${If} $R0 == 1
    SetOutPath "$INSTDIR"  ; shortcut's working directory
    ${LogText} "Creating shortcut: '$DESKTOP\${INSTALL_DIR_AND_SHORTCUT_NAME}.lnk'"
    CreateShortCut "$DESKTOP\${INSTALL_DIR_AND_SHORTCUT_NAME}.lnk" "$productLauncher" "" "" "" SW_SHOWNORMAL
  ${EndIf}
FunctionEnd

; enabling Java assistive technologies if a screen reader is active (0x0046 = SPI_GETSCREENREADER)
Function JavaAssist
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
      CopyFiles /SILENT "$INSTDIR\jbr\bin\WindowsAccessBridge-64.dll" "$SYSDIR"
    ${EndIf}
  ${EndIf}
FunctionEnd


Function .onInit
  SetRegView 64
  ${DisableX64FSRedirection}
  Call createLog

  ${IfNot} ${AtLeastBuild} 14393  ; Windows 10 1607 / Windows Server 2016
    MessageBox MB_OK "$(unsupported_win_version)"
    Abort
  ${EndIf}

  ${GetNativeMachineArchitecture} $R0
  ${If} $R0 <> ${INSTALLER_ARCH}
    IntFmt $R0 "0x%04X" $R0
    IntFmt $R1 "0x%04X" ${INSTALLER_ARCH}
    ${LogText} "Architecture: expected=$R1 actual=$R0"
    ReadEnvStr $0 "TEAMCITY_VERSION"
    ${If} $0 == ""
      ${GetParameters} $0
      ClearErrors
      ${GetOptions} $0 "/IGNORE_ARCH" $1
      ${If} ${Errors}
        ${If} ${INSTALLER_ARCH} = 0x8664
          StrCpy $R1 "x64"
        ${ElseIf} ${INSTALLER_ARCH} = 0xAA64
          StrCpy $R1 "ARM64"
        ${EndIf}
        MessageBox MB_OK "$(arch_mismatch)"
        Abort
      ${Else}
        ${LogText} "  ... overridden"
      ${EndIf}
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
  StrCpy $rootRegKey 1

  IfSilent silent_mode uac_elevate
silent_mode:
  Call CheckAvailableDiskSpace

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
  StrCpy $rootRegKey 0

check_install_dir:
  ${If} $rootRegKey = 1
  ${AndIf} "$INSTDIR" == "${DEFAULT_INST_DIR}"
    StrCpy $INSTDIR "$LOCALAPPDATA\Programs\${INSTALL_DIR_AND_SHORTCUT_NAME}"
  ${EndIf}
  ${If} ${Silent}
    Call OnDirectoryPageLeave ; in the silent mode, check if the installation folder is not empty
  ${EndIf}
  ${LogText} "Root registry key: $rootRegKey (0 - HKLM, 1 - HKCU)"
  ${LogText} "Installation dir: $INSTDIR"

  ${IfNot} ${Silent}
    Call adjustLanguage
    ;!insertmacro MUI_LANGDLL_DISPLAY
  ${EndIf}
FunctionEnd

Function CheckAvailableDiskSpace
  StrCpy $0 $INSTDIR 3  ; copying first 3 characters
  Call GetFreeDiskSpace
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
Function GetFreeDiskSpace
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
  ${DisableX64FSRedirection}

  ; checking that the uninstaller is in the expected location ("...\bin" subdirectory)
  ${IfNot} ${FileExists} "$INSTDIR\${PRODUCT_EXE_FILE}"
    MessageBox MB_OK|MB_ICONEXCLAMATION "$(uninstaller_relocated)"
    Abort
  ${EndIf}

  SetShellVarContext current
  StrCpy $rootRegKey 1

  Call un.FindProductKey
  ${If} $rootRegKey = 0
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
      ExecWait '"$R0" /S _?=$INSTDIR'
    ${Else}
      ExecWait '"$R0" _?=$INSTDIR'
    ${EndIf}
    Delete "$R0"
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
    StrCpy $rootRegKey 0
  ${EndIf}

  ${IfNot} ${Silent}
    Call un.adjustLanguage
    ;!insertmacro MUI_UNGETLANGUAGE
  ${EndIf}

  !insertmacro INSTALLOPTIONS_EXTRACT "DeleteSettings.ini"
  Call un.UninstallFeedback
FunctionEnd

Function un.FindProductKey
  StrCpy $productRegKey ""
  ${GetParent} $INSTDIR $R2

  StrCpy $R0 1  ; HKCU
  StrCpy $R1 "Software"
  Call un.DoFindProductKey
  ${If} $productRegKey != ""
    Return
  ${EndIf}

  StrCpy $R0 0  ; HKLM
  StrCpy $R1 "Software"
  Call un.DoFindProductKey
  ${If} $productRegKey != ""
    Return
  ${EndIf}

  StrCpy $R0 0  ; HKLM
  StrCpy $R1 "Software\Wow6432Node"
  Call un.DoFindProductKey
  ${If} $productRegKey != ""
    Return
  ${EndIf}

  ; registry key is missing; compare $INSTDIR with user locations
  ${UnStrStr} $0 $INSTDIR "$LOCALAPPDATA"
  ${UnStrStr} $1 $INSTDIR "$PROFILE"
  ${IfNot} $0 == $INSTDIR
  ${AndIfNot} $1 == $INSTDIR
    ; unknown location
    StrCpy $rootRegKey 0
  ${EndIf}
FunctionEnd

; $R0 - root key (`0` = HKLM, `1` = HKCU)
; $R1 - base key
; $R2 - installation directory
; $productRegKey(out) - product version key if found
Function un.DoFindProductKey
  StrCpy $8 0
  ${Do}
    ; iterating over manufacturer's products
    ${If} $R0 = 0
      EnumRegKey $7 HKLM "$R1\${MANUFACTURER}" $8
    ${Else}
      EnumRegKey $7 HKCU "$R1\${MANUFACTURER}" $8
    ${EndIf}
    ${If} $7 == ""
      ${Break}
    ${EndIf}

    StrCpy $9 0
    ${Do}
      ; iterating over product's builds
      ${If} $R0 = 0
        EnumRegKey $0 HKLM "$R1\${MANUFACTURER}\$7" $9
      ${Else}
        EnumRegKey $0 HKCU "$R1\${MANUFACTURER}\$7" $9
      ${EndIf}
      ${If} $0 == ""
        ${Break}
      ${EndIf}

      ${If} $R0 = 0
        ReadRegStr $1 HKLM "$R1\${MANUFACTURER}\$7\$0" ""
      ${Else}
        ReadRegStr $1 HKCU "$R1\${MANUFACTURER}\$7\$0" ""
      ${EndIf}
      ${If} $1 == $R2
        StrCpy $rootRegKey $R0
        StrCpy $productRegKey "$R1\${MANUFACTURER}\$7\$0"
        Return
      ${EndIf}

      IntOp $9 $9 + 1
    ${Loop}

    IntOp $8 $8 + 1
  ${Loop}
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
  DetailPrint "Root registry key: $rootRegKey (0 - HKLM, 1 - HKCU)"
  DetailPrint "Product registry key: $productRegKey"

  ; the uninstaller is in the "...\bin" subdirectory; correcting
  ${GetParent} "$INSTDIR" $INSTDIR
  DetailPrint "Uninstalling from: $INSTDIR"

  Call un.checkIfIDEIsRunning

  Call un.customUninstallActions

  Call un.StartMenuShortcut
  Call un.DesktopShortcut
  Call un.UpdatePathEnvVar
  Call un.CachesAndSettings

  ; deleting the uninstaller itself and other cruft
  Delete "$INSTDIR\bin\Uninstall.exe"
  Delete "$INSTDIR\jbr\bin\server\classes.jsa"

  ; main part
  !include "un_idea_win.nsh"
  RMDir "$INSTDIR\bin"
  RMDir "$INSTDIR"

  Call un.UpdateContextMenu
  Call un.ProductAssociation
  Call un.ProductRegistration
  Call un.UninstallRecord

  ${RefreshShellIcons}

  Call un.OpenUninstallFeedbackPage
SectionEnd

Function un.StartMenuShortcut
  StrCpy $0 ""
  ${If} $productRegKey != ""
    ReadRegStr $0 SHCTX $productRegKey "MenuFolder"
  ${EndIf}
  ${If} $0 == ""
    StrCpy $0 "${MANUFACTURER}"
  ${EndIf}
  StrCpy $R0 "$SMPROGRAMS\$0"
  ${If} ${FileExists} $R0
    Call un.DeleteShortcuts
    RMDir $R0  ; if empty
  ${EndIf}
FunctionEnd

Function un.DesktopShortcut
  StrCpy $R0 "$DESKTOP"
  Call un.DeleteShortcuts
FunctionEnd

; $R0 - path to a shortcut directory
Function un.DeleteShortcuts
  FindFirst $0 $1 "$R0\*.lnk"
  ${DoWhile} $1 != ""
    StrCpy $2 "$R0\$1"
    ClearErrors
    ShellLink::GetShortCutTarget "$2"
    Pop $3
    ${IfNot} ${Errors}
    ${AndIf} $3 == "$INSTDIR\bin\${PRODUCT_EXE_FILE}"
      DetailPrint "Deleting shortcut: $2"
      Delete "$2"
    ${EndIf}
    FindNext $0 $1
  ${Loop}
  FindClose $0
FunctionEnd

Function un.UpdatePathEnvVar
  StrCpy $9 0
  ${Do}
    EnumRegValue $0 HKCU "Environment" $9
    ${If} $0 == ""
      ${Break}
    ${EndIf}
    ReadRegStr $1 HKCU "Environment" $0
    ${If} $1 == "$INSTDIR\bin"
      ReadRegStr $1 HKCU "Environment" "Path"
      ${UnStrRep} $2 $1 ";%$0%" ""
      ${If} $2 != $1
      ${AndIf} $2 != ""
        DetailPrint "Updating the 'Path' environment variable."
        WriteRegExpandStr HKCU "Environment" "Path" "$2"
        DetailPrint "Deleting the '$0' environment variable."
        DeleteRegValue HKCU "Environment" $0
        Call un.PostEnvChangeEvent
      ${EndIf}
    ${EndIf}
    IntOp $9 $9 + 1
  ${Loop}
FunctionEnd

Function un.CachesAndSettings
  StrCpy $9 "$LOCALAPPDATA"
  StrCpy $8 "$APPDATA"
  ${If} $rootRegKey = 0
    SetShellVarContext current
    StrCpy $9 "$LOCALAPPDATA"
    StrCpy $8 "$APPDATA"
    SetShellVarContext all
  ${EndIf}

  !insertmacro INSTALLOPTIONS_READ $R2 "DeleteSettings.ini" "Field 4" "State"
  ${If} $R2 == 1
    StrCpy $0 "$9\${MANUFACTURER}\${PRODUCT_PATHS_SELECTOR}"
    DetailPrint "Deleting caches: $0"
    RMDir /R "$0"
    RMDir "$0\.."  ; delete a parent directory if empty
  ${Else}
    DetailPrint "Keeping caches"
  ${EndIf}

  !insertmacro INSTALLOPTIONS_READ $R2 "DeleteSettings.ini" "Field 5" "State"
  ${If} $R2 == 1
    StrCpy $0 "$8\${MANUFACTURER}\${PRODUCT_PATHS_SELECTOR}"
    DetailPrint "Deleting settings: $0"
    RMDir /R "$0"
    RMDir "$0\.."  ; delete a parent directory if empty
  ${Else}
    DetailPrint "Keeping settings"
  ${EndIf}
FunctionEnd

Function un.UpdateContextMenu
  StrCpy $R0 "*"
  Call un.DoUpdateContextMenu
  StrCpy $R0 "Directory"
  Call un.DoUpdateContextMenu
  StrCpy $R0 "Directory\Background"
  Call un.DoUpdateContextMenu
FunctionEnd

; $R0 - a classes subkey
Function un.DoUpdateContextMenu
  StrCpy $9 0
  ${Do}
    EnumRegKey $0 SHCTX "Software\Classes\$R0\shell" $9
    ${If} $0 == ""
      ${Break}
    ${EndIf}
    ReadRegStr $1 SHCTX "Software\Classes\$R0\shell\$0" "Icon"
    ${If} $1 == "$INSTDIR\bin\${PRODUCT_EXE_FILE}"
      DeleteRegKey SHCTX "Software\Classes\$R0\shell\$0"
    ${EndIf}
    IntOp $9 $9 + 1
  ${Loop}
FunctionEnd

Function un.ProductAssociation
  ; looking for the product association key
  StrCpy $R0 ""
  ${If} $productRegKey != ""
    ReadRegStr $R0 SHCTX $productRegKey "AssociationKey"
  ${EndIf}
  ${If} $R0 == ""
    StrCpy $9 0
    ${Do}
      EnumRegKey $0 SHCTX "Software\Classes" $9
      ${If} $0 == ""
        ${Break}
      ${EndIf}
      ReadRegStr $1 SHCTX "Software\Classes\$0\DefaultIcon" ""
      ${If} $1 == "$INSTDIR\bin\${PRODUCT_EXE_FILE},0"
        StrCpy $R0 $0
        ${Break}
      ${EndIf}
      IntOp $9 $9 + 1
    ${Loop}
  ${EndIf}

  ; deleting all associations for the key
  ${If} $R0 != ""
    StrCpy $9 0
    ${Do}
      EnumRegKey $0 SHCTX "Software\Classes" $9
      ${If} $0 == ""
        ${Break}
      ${EndIf}
      ReadRegStr $1 SHCTX "Software\Classes\$0" ""
      ${If} $1 == $R0
        DetailPrint "De-associating from $0"
        ReadRegStr $1 SHCTX "Software\Classes\$0" "backup_val"
        WriteRegStr SHCTX "Software\Classes\$0" "" $1  ; either a previous association or an empty string
        DeleteRegValue SHCTX "Software\Classes\$0" "backup_val"
      ${EndIf}
      IntOp $9 $9 + 1
    ${Loop}

    DeleteRegKey SHCTX "Software\Classes\$R0"
  ${EndIf}

  ; dropping the .ipr association
  ReadRegStr $0 SHCTX "Software\Classes\IntelliJIdeaProjectFile\DefaultIcon" ""
  ${If} $0 == "$INSTDIR\bin\${PRODUCT_EXE_FILE},0"
    ReadRegStr $0 SHCTX "Software\Classes\.ipr" ""
    ${If} $0 == "IntelliJIdeaProjectFile"
      WriteRegStr SHCTX "Software\Classes\.ipr" "" ""
    ${EndIf}
    DeleteRegKey SHCTX "Software\Classes\IntelliJIdeaProjectFile"
  ${EndIf}
FunctionEnd

Function un.ProductRegistration
  ReadRegStr $0 SHCTX "Software\Classes\Applications\${PRODUCT_EXE_FILE}\shell\open\command" ""
  ${If} $0 == '"$INSTDIR\bin\${PRODUCT_EXE_FILE}" "%1"'
    DetailPrint "Unregistering '${PRODUCT_EXE_FILE}' from the application list"
    DeleteRegKey SHCTX "Software\Classes\Applications\${PRODUCT_EXE_FILE}"
  ${EndIf}
FunctionEnd

Function un.UninstallRecord
  StrCpy $R0 "Software"
  Call un.DoUninstallRecord
  StrCpy $R0 "Software\WOW6432Node"
  Call un.DoUninstallRecord

  ${If} $productRegKey != ""
    DeleteRegKey SHCTX $productRegKey
  ${EndIf}
FunctionEnd

; $R0 - base key
Function un.DoUninstallRecord
  StrCpy $9 0
  ${Do}
    EnumRegKey $0 SHCTX "$R0\Microsoft\Windows\CurrentVersion\Uninstall" $9
    ${If} $0 == ""
      ${Break}
    ${EndIf}
    ReadRegStr $1 SHCTX "$R0\Microsoft\Windows\CurrentVersion\Uninstall\$0" "InstallLocation"
    ${If} $1 == $INSTDIR
      DeleteRegKey SHCTX "$R0\Microsoft\Windows\CurrentVersion\Uninstall\$0"
      ${Break}
    ${EndIf}
    IntOp $9 $9 + 1
  ${Loop}
FunctionEnd

Function un.OpenUninstallFeedbackPage
  ${IfNot} ${Silent}
  ${AndIfNot} "${UNINSTALL_WEB_PAGE}" == ""
    !insertmacro INSTALLOPTIONS_READ $R2 "DeleteSettings.ini" "Field 6" "State"
    ${If} $R2 == 1
      ExecShell "" "${UNINSTALL_WEB_PAGE}"
    ${EndIf}
  ${EndIf}
FunctionEnd


;------------------------------------------------------------------------------
; shared code
;------------------------------------------------------------------------------

!macro BLOCK_SWITCH un
  Function ${un}adjustLanguage
    ${If} $Language == ${LANG_SIMPCHINESE}
      System::Call 'kernel32::GetUserDefaultUILanguage() h .r10'
      ${If} $R0 != ${LANG_SIMPCHINESE}
        ${LogText} "Language override: $R0 != ${LANG_SIMPCHINESE}"
        StrCpy $Language ${LANG_ENGLISH}
      ${EndIf}
    ${EndIf}
  FunctionEnd

  Function ${un}PostEnvChangeEvent
    DetailPrint "Notifying applications about environment changes"
    ; SendMessageTimeout(HWND_BROADCAST, WM_SETTINGCHANGE, 0, (LPARAM)"Environment", SMTO_ABORTIFHUNG, 5000, &dwResult)
    System::Call 'user32::SendMessageTimeout(i 0xFFFF, i 0x1A, i 0, t "Environment", i 0x2, i 1000, *i .r1) i .r0'
    IntFmt $0 "0x%x" $0
    DetailPrint "  SendMessageTimeout(): $0, $1"
  FunctionEnd
!macroend

!insertmacro BLOCK_SWITCH ""
!insertmacro BLOCK_SWITCH "un."
