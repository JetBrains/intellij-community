!verbose 2

Unicode true
ManifestDPIAware true
!addplugindir "${NSIS_DIR}\Plugins\x86-unicode"
!addincludedir "${NSIS_DIR}\Include"

SetCompressor lzma

!include "paths.nsi"
!include "strings.nsi"
!include "log.nsi"
!include "registry.nsi"
!include "version.nsi"
!include WinVer.nsh
!include x64.nsh
!define JAVA_REQUIREMENT 1.8
;admin users
;!define Environment '"SYSTEM\CurrentControlSet\Control\Session Manager\Environment"'
;users
!define Environment 'Environment'

; Product with version (IntelliJ IDEA #xxxx).

; Used in registry to put each build info into the separate subkey
; Add&Remove programs doesn't understand subkeys in the Uninstall key,
; thus ${PRODUCT_WITH_VER} is used for uninstall registry information
!define PRODUCT_REG_VER "${MUI_PRODUCT}\${VER_BUILD}"

Name "${MUI_PRODUCT}"
; http://nsis.sourceforge.net/Shortcuts_removal_fails_on_Windows_Vista
RequestExecutionLevel user

;------------------------------------------------------------------------------
; Variables
;------------------------------------------------------------------------------
Var STARTMENU_FOLDER
Var config_path
Var system_path
Var productLauncher
Var baseRegKey
Var downloadJreX86
Var productDir
Var silentMode
Var pathEnvVar
Var requiredDiskSpace

; position of controls for Uninstall Old Installations dialog
Var control_fields
Var max_fields
Var bottom_position
Var max_length
Var line_width
Var extra_space

; position of controls for Installation Options dialog
var launcherShortcut
var secondLauncherShortcut
var addToPath
var downloadJRE
var updateContextMenu

;------------------------------------------------------------------------------
; include "Modern User Interface"
;------------------------------------------------------------------------------
!include "MUI2.nsh"
!include "FileFunc.nsh"
!include "TextFunc.nsh"
!include UAC.nsh
!include "InstallOptions.nsh"
!include StrFunc.nsh
!include LogicLib.nsh

${UnStrStr}
${StrStr}
${StrLoc}
${UnStrLoc}
${UnStrRep}
${StrRep}

!include "customInstallActions.nsi"

ReserveFile "desktop.ini"
ReserveFile "DeleteSettings.ini"
ReserveFile "UninstallOldVersions.ini"
!insertmacro MUI_RESERVEFILE_LANGDLL

!define MUI_ICON "${IMAGES_LOCATION}\${PRODUCT_ICON_FILE}"
!define MUI_UNICON "${IMAGES_LOCATION}\${PRODUCT_UNINST_ICON_FILE}"

!define MUI_HEADERIMAGE
!define MUI_HEADERIMAGE_BITMAP "${IMAGES_LOCATION}\${PRODUCT_HEADER_FILE}"
!define MUI_WELCOMEFINISHPAGE_BITMAP "${IMAGES_LOCATION}\${PRODUCT_LOGO_FILE}"

;------------------------------------------------------------------------------
; on GUI initialization installer checks whether IDEA is already installed
;------------------------------------------------------------------------------

!define MUI_CUSTOMFUNCTION_GUIINIT GUIInit
!macro INST_UNINST_SWITCH un
  ;check if the window is win7 or newer
  Function ${un}winVersion
    ;The platform is returned into $0, minor version into $1.
    ;Windows 7 is equals values of 6 as platform and 1 as minor version.
    ;Windows 8 is equals values of 6 as platform and 2 as minor version.
    ${If} ${AtLeastWin8}
      StrCpy $0 "1"
    ${else}
      StrCpy $0 "0"
    ${EndIf}
  FunctionEnd


  Function ${un}compareFileInstallationTime
    StrCpy $9 ""
  get_first_file:
    Pop $7
    IfFileExists "$7" get_next_file 0
      StrCmp $7 "Complete" complete get_first_file
  get_next_file:
    Pop $8
    StrCmp $8 "Complete" 0 +2
      ; check if there is only one property file
      StrCmp $9 "no changes" complete different
    IfFileExists "$8" 0 get_next_file
    ClearErrors
    ${GetTime} "$7" "M" $0 $1 $2 $3 $4 $5 $6
    ${GetTime} "$8" "M" $R0 $R1 $R2 $R3 $R4 $R5 $R6
    StrCmp $0 $R0 0 different
      StrCmp $1 $R1 0 different
        StrCmp $2 $R2 0 different
          StrCmp $4 $R4 0 different
            StrCmp $5 $R5 0 different
              StrCmp $6 $R6 0 different
		StrCpy $9 "no changes"
		Goto get_next_file
  different:
    StrCpy $9 "Modified"
  complete:
FunctionEnd


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
      StrCmp $4 "" 0 moreleft
        Pop $4
        Pop $3
        Pop $1
        Return
  moreleft:
      Exch $4
      Exch 2
      Pop $1
      Pop $3
      Return
  ${EndIf}
  IntOp $3 $3 + 1
  Goto loop
FunctionEnd


Function ${un}deleteFiles
  ClearErrors
  FindFirst $2 $1 $0\*.*
loop:
  StrCmp $1 "." next 0
  StrCmp $1 ".." next 0
  StrCmp $1 "" done
  Delete "$0\$1"
next:
  FindNext $2 $1
  Goto loop
done:
  FindClose $2
FunctionEnd


Function ${un}deleteDirIfEmpty
  ClearErrors
  FindFirst $R0 $R1 "$0\*.*"
  StrCmp $R1 "." 0 done
  FindNext $R0 $R1
  StrCmp $R1 ".." 0 done
  ClearErrors
  FindNext $R0 $R1
  IfErrors 0 done
  Sleep 1000
  RMDir "$0"
done:
  FindClose $R0
FunctionEnd
!macroend

!insertmacro INST_UNINST_SWITCH ""
!insertmacro INST_UNINST_SWITCH "un."


Function InstDirState
  !define InstDirState `!insertmacro InstDirStateCall`

  !macro InstDirStateCall _PATH _RESULT
    Push `${_PATH}`
    Call InstDirState
    Pop ${_RESULT}
  !macroend

  Exch $0
  Push $1
  ClearErrors

  FindFirst $1 $0 '$0\*.*'
  IfErrors 0 +3
    StrCpy $0 -1
    goto end
  StrCmp $0 '.' 0 +4
    FindNext $1 $0
    StrCmp $0 '..' 0 +2
      FindNext $1 $0
      FindClose $1
  IfErrors 0 +3
    StrCpy $0 0
    goto end
  StrCpy $0 1
end:
  Pop $1
  Exch $0
FunctionEnd


Function SplitFirstStrPart
  Exch $R0
  Exch
  Exch $R1
  Push $R2
  Push $R3
  StrCpy $R3 $R1
  StrLen $R1 $R0
  IntOp $R1 $R1 + 1
loop:
  IntOp $R1 $R1 - 1
  StrCpy $R2 $R0 1 -$R1
  StrCmp $R1 0 exit0
  StrCmp $R2 $R3 exit1 loop
exit0:
  StrCpy $R1 ""
  Goto exit2
exit1:
  IntOp $R1 $R1 - 1
  StrCmp $R1 0 0 +3
     StrCpy $R2 ""
     Goto +2
  StrCpy $R2 $R0 "" -$R1
  IntOp $R1 $R1 + 1
  StrCpy $R0 $R0 -$R1
  StrCpy $R1 $R2
exit2:
  Pop $R3
  Pop $R2
  Exch $R1 ;rest
  Exch
  Exch $R0 ;first
FunctionEnd


Function VersionSplit
  !define VersionSplit `!insertmacro VersionSplitCall`

  !macro VersionSplitCall _FULL _PRODUCT _BRANCH _BUILD
    Push `${_FULL}`
    Call VersionSplit
    Pop ${_PRODUCT}
    Pop ${_BRANCH}
    Pop ${_BUILD}
  !macroend

  Pop $R0
  Push "-"
  Push $R0
  Call SplitFirstStrPart
  Pop $R0
  Pop $R1
  Push "."
  Push $R1
  Call SplitFirstStrPart
  Push $R0
FunctionEnd


Function OnDirectoryPageLeave
  ;check
  ; - if there are no files into $INSTDIR (recursively)
  StrCpy $9 "$INSTDIR"
  Call instDirEmpty
  StrCmp $9 "not empty" abort skip_abort
abort:
  ${LogText} "ERROR: installation dir is not empty: $INSTDIR"
  MessageBox MB_OK|MB_ICONEXCLAMATION "$(empty_or_upgrade_folder)"
  Abort
skip_abort:
FunctionEnd


;check if there are no files into $INSTDIR recursively just except property files.
Function instDirEmpty
  Push $0
  Push $1
  Push $2
  ClearErrors
  FindFirst $1 $2 "$9\*.*"
  IfErrors done 0
next_elemement:
  ;is the element a folder?
  StrCmp $2 "." get_next_element
  StrCmp $2 ".." get_next_element
  IfFileExists "$9\$2\*.*" 0 next_file
    Push $9
    StrCpy "$9" "$9\$2"
    Call instDirEmpty
    StrCmp $9 "not empty" done 0
    Pop $9
    Goto get_next_element
next_file:
  ;is it the file property?
  ${If} $2 != "idea.properties"
    ${AndIf} $2 != "${PRODUCT_EXE_FILE}.vmoptions"
      ${StrRep} $0 ${PRODUCT_EXE_FILE} ".exe" "64.exe.vmoptions"
      ${AndIf} $2 != $0
        StrCpy $9 "not empty"
        Goto done
  ${EndIf}
get_next_element:
  FindNext $1 $2
  IfErrors 0 next_elemement
done:
  ClearErrors
  FindClose $1
  Pop $2
  Pop $1
  Pop $0
FunctionEnd


Function getInstallationOptionsPositions
  !insertmacro INSTALLOPTIONS_READ $launcherShortcut "Desktop.ini" "Settings" "DesktopShortcutToLauncher"
  !insertmacro INSTALLOPTIONS_READ $secondLauncherShortcut "Desktop.ini" "Settings" "DesktopShortcutToSecondLauncher"
  !insertmacro INSTALLOPTIONS_READ $addToPath "Desktop.ini" "Settings" "AddToPath"
  !insertmacro INSTALLOPTIONS_READ $downloadJRE "Desktop.ini" "Settings" "DownloadJRE"
  !insertmacro INSTALLOPTIONS_READ $updateContextMenu "Desktop.ini" "Settings" "UpdateContextMenu"
FunctionEnd


Function ConfirmDesktopShortcut
  !insertmacro MUI_HEADER_TEXT "$(installation_options)" "$(installation_options_prompt)"
  ${StrRep} $0 ${PRODUCT_EXE_FILE} "64.exe" ".exe"
  ${If} $0 == ${PRODUCT_EXE_FILE}
    StrCpy $R0 "32-bit launcher"
    StrCpy $R1 "64-bit launcher"
  ${Else}
    ;there is only one launcher and it is 64-bit.
    StrCpy $R0 "${MUI_PRODUCT} launcher"
    StrCpy $R1 ""
  ${EndIf}

  Call getInstallationOptionsPositions
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $launcherShortcut" "Text" $R0

  ${If} $R1 != ""
    !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $secondLauncherShortcut" "Type" "checkbox"
    !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $secondLauncherShortcut" "Text" $R1
  ${Else}
    Push $R0
    Push $R1
    !insertmacro INSTALLOPTIONS_READ $R0 "Desktop.ini" "Field $secondLauncherShortcut" "Right"
    IntOp $R1 $R0 - 10
    !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $launcherShortcut" "Right" $R1
    IntOp $R1 $R0 - 5
    !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $secondLauncherShortcut" "Left" $R1
    Pop $R1
    Pop $R0
  ${EndIf}

  ; if jre x86 for the build is available then add checkbox to Installation Options dialog
  StrCmp "${LINK_TO_JRE}" "null" custom_pre_actions 0
  inetc::head /SILENT /TOSTACK /CONNECTTIMEOUT 2 ${LINK_TO_JRE} "" /END
  Pop $0
  ${If} $0 == "OK"
    ; download jre x86: optional if OS is not 32-bit
    ${If} ${RunningX64}
      StrCpy $downloadJreX86 "0"
    ${Else}
      ; download jre32
      StrCpy $downloadJreX86 "1"
      !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $downloadJRE" "Flags" "DISABLED"

      ; create shortcut for launcher 32
      !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $launcherShortcut" "State" "1"
      !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $launcherShortcut" "Flags" "DISABLED"
    ${EndIf}
    !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $downloadJRE" "Type" "checkbox"
    !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $downloadJRE" "State" $downloadJreX86
    !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $downloadJRE" "Text" "Download and install JRE x86 by JetBrains"
  ${EndIf}
custom_pre_actions:
  Call customPreInstallActions
  SetRegView 32
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


Function downloadJre
  !insertmacro INSTALLOPTIONS_READ $R0 "Desktop.ini" "Field $downloadJRE" "State"
  ${If} $R0 == 1
    inetc::get ${LINK_TO_JRE} "$TEMP\jre.tar.gz" /END
    Pop $0
    ${If} $0 == "OK"
      untgz::extract "-d" "$INSTDIR\jre32" "$TEMP\jre.tar.gz"
      StrCmp $R0 "success" remove_temp_jre
      ${LogText} "ERROR: jre32: Failed to extract"
      DetailPrint "Failed to extract jre.tar.gz"
      MessageBox MB_OK|MB_ICONEXCLAMATION|MB_DEFBUTTON1 "Failed to extract $TEMP\jre.tar.gz"
      Goto clean
remove_temp_jre:
      ${LogText} "jre32: extracted"
clean:
      IfFileExists "$TEMP\jre.tar.gz" 0 done
      Delete "$TEMP\jre.tar.gz"
    ${Else}
      ${LogText} "ERROR: jre32: download ${LINK_TO_JRE} is failed: $0"
      MessageBox MB_OK|MB_ICONEXCLAMATION "The ${LINK_TO_JRE} download is failed: $0"
    ${EndIf}
  ${EndIf}
done:
FunctionEnd

;------------------------------------------------------------------------------
; configuration
;------------------------------------------------------------------------------

!insertmacro MUI_PAGE_WELCOME
Page custom uninstallOldVersionDialog

!ifdef LICENSE_FILE
!insertmacro MUI_PAGE_LICENSE "$(myLicenseData)"
!endif

!define MUI_PAGE_CUSTOMFUNCTION_LEAVE OnDirectoryPageLeave
!define MUI_PAGE_HEADER_TEXT "$(choose_install_location)"
!insertmacro MUI_PAGE_DIRECTORY

Page custom ConfirmDesktopShortcut
  !define MUI_PAGE_HEADER_TEXT "$(choose_start_menu_folder)"
  !define MUI_STARTMENUPAGE_NODISABLE
  !define MUI_STARTMENUPAGE_DEFAULTFOLDER "JetBrains"

!insertmacro MUI_PAGE_STARTMENU Application $STARTMENU_FOLDER
!define MUI_ABORTWARNING

!define MUI_PAGE_HEADER_TEXT "$(installing_product)"
!insertmacro MUI_PAGE_INSTFILES

!define MUI_FINISHPAGE_RUN_NOTCHECKED
!define MUI_FINISHPAGE_REBOOTLATER_DEFAULT
!define MUI_FINISHPAGE_RUN
!define MUI_FINISHPAGE_RUN_FUNCTION PageFinishRun
!insertmacro MUI_PAGE_FINISH

!define MUI_UNINSTALLER
;!insertmacro MUI_UNPAGE_CONFIRM
UninstPage custom un.ConfirmDeleteSettings
!insertmacro MUI_UNPAGE_INSTFILES

OutFile "${OUT_DIR}\${OUT_FILE}.exe"

InstallDir "$PROGRAMFILES\${MANUFACTURER}\${PRODUCT_WITH_VER}"
!define MUI_BRANDINGTEXT " "
BrandingText " "

Function PageFinishRun
  ${If} ${RunningX64}
    !insertmacro UAC_AsUser_ExecShell "" "${PRODUCT_EXE_FILE_64}" "" "$INSTDIR\bin" ""
  ${Else}
    !insertmacro UAC_AsUser_ExecShell "" "${PRODUCT_EXE_FILE}" "" "$INSTDIR\bin" ""
  ${EndIf}
FunctionEnd

;------------------------------------------------------------------------------
; languages
;------------------------------------------------------------------------------
!insertmacro MUI_LANGUAGE "English"
;!insertmacro MUI_LANGUAGE "Japanese"
!include "idea_en.nsi"
;!include "idea_jp.nsi"

!ifdef LICENSE_FILE
LicenseLangString myLicenseData ${LANG_ENGLISH} "${LICENSE_FILE}.txt"
LicenseLangString myLicenseData ${LANG_JAPANESE} "${LICENSE_FILE}.txt"
!endif


Function .onInstSuccess
  SetErrorLevel 0
  ${LogText} "Installation has been finished successfully."
FunctionEnd


function silentInstallDirValidate
; use current user path as install dir if installation run in user mode
  push $0
  ${If} $silentMode == "user"
    ${StrLoc} $0 $INSTDIR "$PROGRAMFILES\${MANUFACTURER}" ">"
    StrCmp $0 "" check_if_install_dir_contains_PROGRAMFILES64 update_install_dir
check_if_install_dir_contains_PROGRAMFILES64:
    ${StrLoc} $0 $INSTDIR "$PROGRAMFILES64\${MANUFACTURER}" ">"
    StrCmp $0 "" done update_install_dir
update_install_dir:
    ${LogText} ""
    ${LogText} "  NOTE: Specified install dir: $INSTDIR is required administrative rights."
    ${LogText} "  It is corresponding with the admin mode in silent config file."
    ${LogText} "  But installation has been run with user mode. So install folder has been changed to the default: "
    StrCpy $INSTDIR "$LOCALAPPDATA\${MANUFACTURER}\${PRODUCT_WITH_VER}"
    ${LogText} "  $INSTDIR "
    ${LogText} ""
  ${EndIf}
done:
  pop $0
  ${LogText} "Silent installation dir: $INSTDIR"
FunctionEnd


Function silentConfigReader
  ; read Desktop.ini
  ${LogText} ""
  ${LogText} "Silent installation, options"
  Call getInstallationOptionsPositions
  ${GetParameters} $R0
  ClearErrors

  ${GetOptions} $R0 /CONFIG= $R1
  IfErrors no_silent_config
  ${LogText} "  config file: $R1"

  ${ConfigRead} "$R1" "mode=" $R0
  IfErrors no_silent_config
  ${LogText} "  mode: $R0"
  StrCpy $silentMode "user"
  IfErrors launcher_32
  StrCpy $silentMode $R0

launcher_32:
  ClearErrors
  ${ConfigRead} "$R1" "launcher32=" $R3
  IfErrors launcher_64
  ${LogText} "  shortcut for launcher32: $R3"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $launcherShortcut" "State" $R3

launcher_64:
  ClearErrors
  ${ConfigRead} "$R1" "launcher64=" $R3
  IfErrors update_PATH
  ${LogText} "  shortcut for launcher64: $R3"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $secondLauncherShortcut" "Type" "checkbox"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $secondLauncherShortcut" "State" $R3

update_PATH:
  ClearErrors
  ${ConfigRead} "$R1" "updatePATH=" $R3
  IfErrors update_context_menu
  ${LogText} "  update PATH env var: $R3"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $addToPath" "Type" "checkbox"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $addToPath" "State" $R3

update_context_menu:
  ClearErrors
  ${ConfigRead} "$R1" "updateContextMenu=" $R3
  IfErrors download_jre32
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $updateContextMenu" "Type" "checkbox"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $updateContextMenu" "State" $R3

download_jre32:
  ClearErrors
  ${ConfigRead} "$R1" "jre32=" $R3
  IfErrors associations
  ${LogText} "  download jre32: $R3"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $downloadJRE" "Type" "checkbox"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $downloadJRE" "State" $R3

associations:
  ClearErrors
  StrCmp "${ASSOCIATION}" "NoAssociation" done
  !insertmacro INSTALLOPTIONS_READ $R0 "Desktop.ini" "Settings" "NumFields"
  push "${ASSOCIATION}"
loop:
  call SplitStr
  Pop $0
  StrCmp $0 "" update_settings
  ClearErrors
  ${ConfigRead} "$R1" "$0=" $R3
  IfErrors update_settings
  IntOp $R0 $R0 + 1
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $R0" "State" $R3
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $R0" "Text" "$0"
  ${LogText} "  association: $0, state: $R3"
  goto loop

update_settings:
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Settings" "NumFields" "$R0"
  goto done
no_silent_config:
  Call IncorrectSilentInstallParameters
done:
FunctionEnd


Function IncorrectSilentInstallParameters
  !define msg1 "How to run installation in silent mode:$\r$\n"
  !define msg2 "<installation> /S /CONFIG=<path to silent config with file name> /D=<install dir>$\r$\n$\r$\n"
  !define msg3 "Examples:$\r$\n"
  !define msg4 "Installation.exe /S /CONFIG=d:\download\silent.config /D=d:\JetBrains\Product$\r$\n"
  !define msg5 "Run installation in silent mode with logging:$\r$\n"
  !define msg6 "Installation.exe /S /CONFIG=d:\download\silent.config /LOG=d:\JetBrains\install.log /D=d:\JetBrains\Product$\r$\n"
  MessageBox MB_OK|MB_ICONSTOP "${msg1}${msg2}${msg3}${msg4}${msg5}${msg6}"
  ${LogText} "ERROR: silent installation: incorrect parameters."
  Abort
FunctionEnd


Function checkVersion
  StrCpy $2 ""
  StrCpy $1 "Software\${MANUFACTURER}\${PRODUCT_REG_VER}"
  Call OMReadRegStr
  IfFileExists $3\bin\${PRODUCT_EXE_FILE} check_version
  Goto done
check_version:
  StrCpy $9 $3
  StrCpy $2 "Build"
  Call OMReadRegStr
  StrCmp $3 "" done
  IntCmpU $3 ${VER_BUILD} ask_Install_Over done ask_Install_Over
ask_Install_Over:
  ${LogText} "  NOTE: ${PRODUCT_WITH_VER} is already installed:"
  ${LogText} "  $9"
  ${LogText} ""
  IfSilent continue 0
  MessageBox MB_YESNO|MB_ICONQUESTION "$(current_version_already_installed)" IDYES continue IDNO exit_installer
exit_installer:
  Abort
continue:
  StrCpy $0 "complete"
done:
FunctionEnd


Function searchCurrentVersion
  ${LogText} ""
  ${LogText} "Check if ${MUI_PRODUCT} ${VER_BUILD} already installed"
  ; search current version of IDEA
  StrCpy $0 "HKCU"
  Call checkVersion
  StrCmp $0 "complete" Done
  StrCpy $0 "HKLM"
  Call checkVersion
Done:
FunctionEnd


Function uninstallOldVersion
  ;uninstallation mode
  !insertmacro INSTALLOPTIONS_READ $9 "UninstallOldVersions.ini" "Field 2" "State"
  ${LogText} ""
  ${LogText} "Uninstall old installation: $3"

  ;do copy for unistall.exe
  CopyFiles "$3\bin\Uninstall.exe" "$LOCALAPPDATA\${PRODUCT_PATHS_SELECTOR}_${VER_BUILD}_Uninstall.exe"

  ${If} $9 == "1"
    ExecWait '"$LOCALAPPDATA\${PRODUCT_PATHS_SELECTOR}_${VER_BUILD}_Uninstall.exe" /S /NO_UNINSTALL_FEEDBACK=true _?=$3\bin'
  ${else}
    ExecWait '"$LOCALAPPDATA\${PRODUCT_PATHS_SELECTOR}_${VER_BUILD}_Uninstall.exe" /NO_UNINSTALL_FEEDBACK=true _?=$3\bin'
  ${EndIf}
  IfFileExists $3\bin\${PRODUCT_EXE_FILE} 0 uninstall
  goto complete
uninstall:
  ;previous installation has been removed
  ;customer has decided to keep properties?
  Delete "$LOCALAPPDATA\${PRODUCT_PATHS_SELECTOR}_${VER_BUILD}_Uninstall.exe"
complete:
FunctionEnd


Function checkProductVersion
;$8 - count of already added fields to the dialog
;$3 - an old version which will be checked if the one should be added too
  StrCpy $7 $control_fields
  StrCpy $6 ""
loop:
  IntOp $7 $7 + 1
  ${If} $8 >= $7
    !insertmacro INSTALLOPTIONS_READ $6 "UninstallOldVersions.ini" "Field $7" "Text"
    ${If} $6 == $3
      ;found the same value in list of installations
      StrCpy $6 "duplicated"
      Goto finish
    ${EndIf}
    Goto loop
  ${EndIf}
finish:
FunctionEnd


Function getUninstallOldVersionVars
  !insertmacro INSTALLOPTIONS_READ $max_fields "UninstallOldVersions.ini" "Settings" "NumFields"
  !insertmacro INSTALLOPTIONS_READ $control_fields "UninstallOldVersions.ini" "Settings" "ControlFields"
  !insertmacro INSTALLOPTIONS_READ $bottom_position "UninstallOldVersions.ini" "Settings" "BottomPosition"
  !insertmacro INSTALLOPTIONS_READ $max_length "UninstallOldVersions.ini" "Settings" "MaxLength"
  !insertmacro INSTALLOPTIONS_READ $line_width "UninstallOldVersions.ini" "Settings" "LineWidth"
  !insertmacro INSTALLOPTIONS_READ $extra_space "UninstallOldVersions.ini" "Settings" "ExtraSpace"
FunctionEnd


Function getPosition
; return:
;    0 if it is first checkbox which do not require special position
;    Bottom position of previous checkbox which equals for Top position of current one.
  IntOp $R8 $8 - 1
  !insertmacro INSTALLOPTIONS_READ $R7 "UninstallOldVersions.ini" "Field $R8" "Bottom"
  !insertmacro INSTALLOPTIONS_READ $7  "UninstallOldVersions.ini" "Field $8"  "Top"
  StrCmp $R8 $control_fields noCheckboxesFound 0
    Push $R7
    Goto done
noCheckboxesFound:
    Push $7
done:
FunctionEnd


Function getAdditionalSpaceForCheckbox
; $3 - a path to an old installation
; return
;   - 0 for 1-line checkbox
;   - a value for additional space for multi-line checkbox
  StrLen $9 $3
  ${If} $9 >= $max_length
    ; installation path is long
    Push $extra_space
    Goto done
  ${Else}
    Push 0
  ${EndIf}
done:
FunctionEnd


Function haveSpaceForTheCheckbox
  ; check if dialog has space for current checkbox
  !insertmacro INSTALLOPTIONS_READ $7 "UninstallOldVersions.ini" "Field $8" "Bottom"
  IntOp $7 $bottom_position - $7
  ${If} $7 >= 0
    Push 0
    Goto done
  ${Else}
    IntOp $8 $8 - 1
    Push 1
  ${EndIf}
done:
FunctionEnd


Function uninstallOldVersionDialog
  StrCpy $0 "HKLM"
  StrCpy $4 0
  StrCpy $8 $control_fields
  !insertmacro INSTALLOPTIONS_WRITE "UninstallOldVersions.ini" "Field 2" "State" "0"

get_installation_info:
  StrCpy $1 "Software\${MANUFACTURER}\${MUI_PRODUCT}"
  StrCpy $5 "\bin\${PRODUCT_EXE_FILE}"
  StrCpy $2 ""
  Call getInstallationPath
  StrCmp $3 "complete" next_registry_root
  ;check if the old installation could be uninstalled
  IfFileExists $3\bin\Uninstall.exe uninstall_dialog get_next_key
uninstall_dialog:
  Call checkProductVersion
  ${If} $6 != "duplicated"
    IntOp $8 $8 + 1
    Call getPosition
    Pop $7
    !insertmacro INSTALLOPTIONS_WRITE "UninstallOldVersions.ini" "Field $8" "Top" "$7"
    IntOp $R7 $7 + $line_width
    Call getAdditionalSpaceForCheckbox
    Pop $R9
    IntOp $R7 $R7 + $R9
    !insertmacro INSTALLOPTIONS_WRITE "UninstallOldVersions.ini" "Field $8" "Bottom" "$R7"
    !insertmacro INSTALLOPTIONS_WRITE "UninstallOldVersions.ini" "Field $8" "State" "0"
    !insertmacro INSTALLOPTIONS_WRITE "UninstallOldVersions.ini" "Field $8" "Text" "$3"
    Call haveSpaceForTheCheckbox
    Pop $9
    StrCmp $9 0 0 complete
  ${EndIf}
get_next_key:
  IntOp $4 $4 + 1 ;next record from registry
  goto get_installation_info

next_registry_root:
  ${If} $0 == "HKLM"
    StrCpy $0 "HKCU"
    StrCpy $4 0
    Goto get_installation_info
  ${EndIf}

complete:
  !insertmacro INSTALLOPTIONS_WRITE "UninstallOldVersions.ini" "Settings" "NumFields" "$8"
  ${If} $8 > $control_fields
    ;$2 used in prompt text
    StrCpy $2 "s"
    StrCpy $7 $control_fields
    IntOp $7 $7 + 1
    StrCmp $8 $7 0 +2
      StrCpy $2 ""
    !insertmacro MUI_HEADER_TEXT "$(uninstall_previous_installations_title)" "$(uninstall_previous_installations)"
    !insertmacro INSTALLOPTIONS_WRITE "UninstallOldVersions.ini" "Field 1" "Text" "$(uninstall_previous_installations_prompt)"
    !insertmacro INSTALLOPTIONS_WRITE "UninstallOldVersions.ini" "Field 3" "Flags" "FOCUS"
    !insertmacro INSTALLOPTIONS_DISPLAY_RETURN "UninstallOldVersions.ini"
    Pop $9
    ${If} $9 == "success"
loop:
      ;uninstall chosen installation(s)
      !insertmacro INSTALLOPTIONS_READ $0 "UninstallOldVersions.ini" "Field $8" "State"
      !insertmacro INSTALLOPTIONS_READ $3 "UninstallOldVersions.ini" "Field $8" "Text"
      ${If} $0 == "1"
        Call uninstallOldVersion
      ${EndIf}
      IntOp $8 $8 - 1
      StrCmp $8 $control_fields finish loop
    ${EndIf}
  ${EndIf}
finish:
FunctionEnd


Function getInstallationPath
  Push $1
  Push $2
  Push $5
loop:
  Call OMEnumRegKey
  StrCmp $3 "" 0 getPath
  StrCpy $3 "complete"
  goto done
getPath:
  Push $1
  StrCpy $1 "$1\$3"
  Call OMReadRegStr
  Pop $1
  IfFileExists $3$5 done 0
  IntOp $4 $4 + 1
  goto loop
done:
  Pop $5
  Pop $2
  Pop $1
FunctionEnd


Function GUIInit
  Push $0
  Push $1
  Push $2
  Push $3
  Push $4
  Push $5

; is the current version of IDEA installed?
  Call searchCurrentVersion

; search old versions of IDEA installed from the user and admin.
  ${LogText} "Search if old versions of ${MUI_PRODUCT} were installed"

user:
  StrCpy $4 0
  StrCpy $0 "HKCU"
  StrCpy $1 "Software\${MANUFACTURER}\${MUI_PRODUCT}"
  StrCpy $5 "\bin\${PRODUCT_EXE_FILE}"
  StrCpy $2 ""
  Call getInstallationPath
  StrCmp $3 "complete" admin
  IfFileExists $3\bin\${PRODUCT_EXE_FILE} collect_versions admin
admin:
  StrCpy $4 0
  StrCpy $0 "HKLM"
  Call getInstallationPath

collect_versions:
  IntCmp ${SHOULD_SET_DEFAULT_INSTDIR} 0 end_enum_versions_hklm
; latest build number and registry key index
  StrCpy $3 "0"
  StrCpy $0 "0"

enum_versions_hkcu:
  EnumRegKey $1 "HKCU" "Software\${MANUFACTURER}\${MUI_PRODUCT}" $0
  StrCmp $1 "" end_enum_versions_hkcu
  IntCmp $1 $3 continue_enum_versions_hkcu continue_enum_versions_hkcu
  StrCpy $3 $1
  ReadRegStr $INSTDIR "HKCU" "Software\${MANUFACTURER}\${MUI_PRODUCT}\$3" ""

continue_enum_versions_hkcu:
  IntOp $0 $0 + 1
  Goto enum_versions_hkcu

end_enum_versions_hkcu:
  StrCpy $0 "0"        # registry key index

enum_versions_hklm:
  EnumRegKey $1 "HKLM" "Software\${MANUFACTURER}\${MUI_PRODUCT}" $0
  StrCmp $1 "" end_enum_versions_hklm
  IntCmp $1 $3 continue_enum_versions_hklm continue_enum_versions_hklm
  StrCpy $3 $1
  ReadRegStr $INSTDIR "HKLM" "Software\${MANUFACTURER}\${MUI_PRODUCT}\$3" ""

continue_enum_versions_hklm:
  IntOp $0 $0 + 1
  Goto enum_versions_hklm

end_enum_versions_hklm:
  StrCmp $INSTDIR "" 0 skip_default_instdir
  ${If} ${RunningX64}
    StrCpy $INSTDIR "$PROGRAMFILES64\${MANUFACTURER}\${MUI_PRODUCT} ${MUI_VERSION_MAJOR}.${MUI_VERSION_MINOR}"
  ${Else}
    StrCpy $INSTDIR "$PROGRAMFILES\${MANUFACTURER}\${MUI_PRODUCT} ${MUI_VERSION_MAJOR}.${MUI_VERSION_MINOR}"
  ${EndIf}

skip_default_instdir:
  Pop $5
  Pop $4
  Pop $3
  Pop $2
  Pop $1
  Pop $0
  !insertmacro INSTALLOPTIONS_EXTRACT "Desktop.ini"
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
  ${LogText} "Update Context Menu"

; add "Open with PRODUCT" action for files to Windows context menu
  StrCpy $0 "SHCTX"
  StrCpy $1 "Software\Classes\*\shell\Open with ${MUI_PRODUCT}"
  StrCpy $2 ""
  StrCpy $3 "Edit with ${MUI_PRODUCT}"
  call OMWriteRegStr

  StrCpy $0 "SHCTX"
  StrCpy $1 "Software\Classes\*\shell\Open with ${MUI_PRODUCT}"
  StrCpy $2 "Icon"
  StrCpy $3 "$productLauncher"
  call OMWriteRegStr

  StrCpy $0 "SHCTX"
  StrCpy $1 "Software\Classes\*\shell\Open with ${MUI_PRODUCT}\command"
  StrCpy $2 ""
  StrCpy $3 '"$productLauncher" "%1"'
  call OMWriteRegStr

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
  StrCpy $3 " $productLauncher,0"
  Call OMWriteRegStr
  StrCpy $1 "$R5\shell\open\command"
  StrCpy $2 ""
  StrCpy $3 '"$productLauncher" "%1"'
  Call OMWriteRegStr
  pop $3
  pop $2
  pop $1
  pop $0
FunctionEnd


Function getPathEnvVar
  ${LogText} "  get value of user's PATH env var"
  ClearErrors
  ReadRegStr $pathEnvVar HKCU ${Environment} "Path"
  IfErrors do_not_change_path ;size of PATH is more than NSIS_MAX_STRLEN
  ${LogText} "  PATH: $pathEnvVar"
  Goto done
do_not_change_path:
  ${LogText} "  an error occured on readyng value of PATH env var"
  StrCpy $pathEnvVar ""
done:
FunctionEnd


Function createProductEnvVar
  WriteRegStr HKCU ${Environment} "${MUI_PRODUCT}" "$INSTDIR\bin;"
  ${LogText} "  create product env var: ${MUI_PRODUCT} $INSTDIR\bin;"
FunctionEnd


Function updatePathEnvVar
  StrCmp $pathEnvVar "" do_not_change_path 0
  ${StrStr} $R0 $pathEnvVar "%${MUI_PRODUCT}%"
  StrCmp $R0 "" absent done
absent:
  WriteRegExpandStr HKCU ${Environment} "Path" "$pathEnvVar;%${MUI_PRODUCT}%"
  ${LogText} "  update PATH: HKCU ${Environment} Path $pathEnvVar;%${MUI_PRODUCT}%"
  Goto done
do_not_change_path:
  ${LogText} ""
  ${LogText} "  NOTE: Length of PATH is bigger than 8192 bytes."
  ${LogText} "  Installer can not update it."
  ${LogText} ""
  MessageBox MB_OK|MB_ICONEXCLAMATION "Length of PATH is bigger than 8192 bytes.$\r$\nInstaller can not update it."
done:
FunctionEnd


;------------------------------------------------------------------------------
; Installer sections
;------------------------------------------------------------------------------
Section "IDEA Files" CopyIdeaFiles
  CreateDirectory $INSTDIR
  Call customInstallActions
  SetRegView 32

  ;define launcher in accordingly to OS version
  ${If} ${RunningX64}
     StrCpy $productLauncher "$INSTDIR\bin\${PRODUCT_EXE_FILE_64}"
  ${Else}
     StrCpy $productLauncher "$INSTDIR\bin\${PRODUCT_EXE_FILE}"
  ${EndIf}
  ${LogText} "Default launcher: $productLauncher"
  DetailPrint "Default launcher: $productLauncher"

  StrCmp "${LINK_TO_JRE}" "null" shortcuts 0
  ;download and install JRE x86
  Call downloadJre

shortcuts:
  !insertmacro INSTALLOPTIONS_READ $R2 "Desktop.ini" "Field $launcherShortcut" "State"
  StrCmp $R2 1 "" exe_64
  CreateShortCut "$DESKTOP\${PRODUCT_FULL_NAME_WITH_VER}.lnk" \
                 "$INSTDIR\bin\${PRODUCT_EXE_FILE}" "" "" "" SW_SHOWNORMAL
  ${LogText} "Create shortcut: $DESKTOP\${PRODUCT_FULL_NAME_WITH_VER}.lnk $INSTDIR\bin\${PRODUCT_EXE_FILE}"
exe_64:
  !insertmacro INSTALLOPTIONS_READ $R2 "Desktop.ini" "Field $secondLauncherShortcut" "State"
  StrCmp $R2 1 "" add_to_path
  CreateShortCut "$DESKTOP\${PRODUCT_FULL_NAME_WITH_VER} x64.lnk" \
                 "$INSTDIR\bin\${PRODUCT_EXE_FILE_64}" "" "" "" SW_SHOWNORMAL
  ${LogText} "Create shortcut: $DESKTOP\${PRODUCT_FULL_NAME_WITH_VER} x64.lnk $INSTDIR\bin\${PRODUCT_EXE_FILE_64}"

add_to_path:
  !insertmacro INSTALLOPTIONS_READ $R0 "Desktop.ini" "Field $addToPath" "State"
  ${If} $R0 == 1
    ${LogText} "Update PATH env var"
    Call getPathEnvVar
    Call createProductEnvVar
    CALL updatePathEnvVar
    SetRebootFlag true
  ${EndIf}

update_context_menu:
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
  WriteRegStr HKCR "IntelliJIdeaProjectFile\shell\open\command" "" \
    '"$productLauncher" "%1"'
!undef Index

skip_ipr:
; readonly section
  ${LogText} ""
  ${LogText} "Copy files to $INSTDIR"
  SectionIn RO
  !include "idea_win.nsh"

  SetOutPath $INSTDIR\bin
  File "${PRODUCT_PROPERTIES_FILE}"
  File "${PRODUCT_VM_OPTIONS_FILE}"

; registration application to be presented in Open With list
  call ProductRegistration
!insertmacro MUI_STARTMENU_WRITE_BEGIN Application
; $STARTMENU_FOLDER stores name of IDEA folder in Start Menu,
; save it name in the "MenuFolder" RegValue
  CreateDirectory "$SMPROGRAMS\$STARTMENU_FOLDER"
  CreateShortCut "$SMPROGRAMS\$STARTMENU_FOLDER\${PRODUCT_FULL_NAME_WITH_VER}.lnk" \
                 "$productLauncher" "" "" "" SW_SHOWNORMAL

  StrCpy $7 "$SMPROGRAMS\$STARTMENU_FOLDER\${PRODUCT_FULL_NAME_WITH_VER}.lnk"
  ShellLink::GetShortCutWorkingDirectory $7
  Pop $0
  DetailPrint "ShortCutWorkingDirectory: $0"
  ${LogText} ""
  ${LogText} "ShortCutWorkingDirectory: $0"

  StrCpy $0 $baseRegKey
  StrCpy $1 "Software\${MANUFACTURER}\${PRODUCT_REG_VER}"
  StrCpy $2 "MenuFolder"
  StrCpy $3 "$STARTMENU_FOLDER"
  Call OMWriteRegStr
!insertmacro MUI_STARTMENU_WRITE_END

  Call customPostInstallActions
  SetRegView 32
  StrCpy $0 $baseRegKey
  StrCpy $1 "Software\${MANUFACTURER}\${PRODUCT_REG_VER}"
  StrCpy $2 ""
  StrCpy $3 "$INSTDIR"
  Call OMWriteRegStr
  StrCpy $2 "Build"
  StrCpy $3 ${VER_BUILD}
  Call OMWriteRegStr

; write uninstaller & add it to add/remove programs in control panel
  WriteUninstaller "$INSTDIR\bin\Uninstall.exe"
  WriteRegStr SHCTX "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_WITH_VER}" \
            "DisplayName" "${PRODUCT_FULL_NAME_WITH_VER}"
  WriteRegStr SHCTX "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_WITH_VER}" \
              "UninstallString" "$INSTDIR\bin\Uninstall.exe"
  WriteRegStr SHCTX "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_WITH_VER}" \
              "InstallLocation" "$INSTDIR"
  WriteRegStr SHCTX "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_WITH_VER}" \
              "DisplayIcon" "$productLauncher"
  WriteRegStr SHCTX "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_WITH_VER}" \
              "DisplayVersion" "${VER_BUILD}"
  WriteRegStr SHCTX "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_WITH_VER}" \
              "Publisher" "JetBrains s.r.o."
  WriteRegStr SHCTX "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_WITH_VER}" \
              "URLInfoAbout" "https://www.jetbrains.com/products"
  WriteRegStr SHCTX "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_WITH_VER}" \
              "InstallType" "$baseRegKey"
  WriteRegDWORD SHCTX "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_WITH_VER}" \
              "NoModify" 1
  WriteRegDWORD SHCTX "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_WITH_VER}" \
              "NoRepair" 1

  ; Regenerating the Shared Archives for java x64 and x86 bit.
  ; http://docs.oracle.com/javase/8/docs/technotes/guides/vm/class-data-sharing.html
  IfFileExists $INSTDIR\jre64\bin\javaw.exe 0 skip_regeneration_shared_archive_for_java_64
  ${LogText} ""
  ${LogText} "Regenerating the Shared Archives for java 64"
  ExecDos::exec /NOUNLOAD /ASYNC '"$INSTDIR\jre64\bin\javaw.exe" -Xshare:dump'

skip_regeneration_shared_archive_for_java_64:
  SetOutPath $INSTDIR\bin
; set the current time for installation files under $INSTDIR\bin
  ExecDos::exec 'copy "$INSTDIR\bin\*.*s" +,,'
  call winVersion
  ${If} $0 == "1"
    ;ExecCmd::exec 'icacls "$INSTDIR" /grant %username%:F /T >"$INSTDIR"\installation_log.txt 2>"$INSTDIR"\installation_error.txt'
    AccessControl::GrantOnFile \
      "$INSTDIR" "(S-1-5-32-545)" "GenericRead + GenericExecute"
    AccessControl::GrantOnFile \
      "$INSTDIR\bin\${PRODUCT_EXE_FILE}.vmoptions" "(S-1-5-32-545)" "GenericRead + GenericWrite"
    ${StrRep} $0 ${PRODUCT_EXE_FILE} ".exe" "64.exe.vmoptions"
    AccessControl::GrantOnFile \
      "$INSTDIR\bin\$0" "(S-1-5-32-545)" "GenericRead + GenericWrite"
  ${EndIf}

; reset icon cache
  ${LogText} "Reset icon cache"
  System::Call 'shell32.dll::SHChangeNotify(i, i, i, i) v (0x08000000, 0, 0, 0)'
SectionEnd


Function .onInit
  SetRegView 32
  Call createLog
  !insertmacro INSTALLOPTIONS_EXTRACT "UninstallOldVersions.ini"
  !insertmacro INSTALLOPTIONS_EXTRACT "Desktop.ini"
  Call getInstallationOptionsPositions
  Call getUninstallOldVersionVars
  IfSilent silent_mode uac_elevate

silent_mode:
  Call checkAvailableRequiredDiskSpace
  IntCmp ${CUSTOM_SILENT_CONFIG} 0 silent_config silent_config custom_silent_config

silent_config:
  Call silentConfigReader
  Goto validate_install_dir
custom_silent_config:
  Call customSilentConfigReader

validate_install_dir:
  Call searchCurrentVersion
  Call silentInstallDirValidate
set_reg_key:
  StrCpy $baseRegKey "HKCU"
  StrCmp $silentMode "admin" uac_elevate installdir_is_empty
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
  ${LogText} "  NOTE: UAC elevation has been aborted. Installation dir will be changed."
  ${LogText} ""
  StrCpy $INSTDIR "$LOCALAPPDATA\${MANUFACTURER}\${PRODUCT_WITH_VER}"
  goto installdir_is_empty
uac_success:
  StrCmp 1 $3 uac_admin ;Admin?
  StrCmp 3 $1 0 uac_elevation_aborted ;Try again?
  goto uac_elevate
uac_admin:
  IfSilent uac_all_users set_install_dir_admin_mode
set_install_dir_admin_mode:
  ${If} ${RunningX64}
    StrCpy $INSTDIR "$PROGRAMFILES64\${MANUFACTURER}\${PRODUCT_WITH_VER}"
  ${Else}
    StrCpy $INSTDIR "$PROGRAMFILES\${MANUFACTURER}\${PRODUCT_WITH_VER}"
  ${EndIf}
uac_all_users:
  SetShellVarContext all
  StrCpy $baseRegKey "HKLM"
installdir_is_empty:
  IfSilent 0 done
; Check in silent mode if install folder is not empty.
  Call OnDirectoryPageLeave
done:
  ${LogText} "Installation dir: $INSTDIR"
;  !insertmacro MUI_LANGDLL_DISPLAY
FunctionEnd


Function checkAvailableRequiredDiskSpace
  SectionGetSize ${CopyIdeaFiles} $requiredDiskSpace
  ${LogText} "Space required: $requiredDiskSpace KB"
  Push $INSTDIR
  StrCpy $9 $INSTDIR 3
  Call FreeDiskSpace
  ${LogText} "Space available: $1 KB"

; required free space
  StrCpy $2 $requiredDiskSpace
; compare the space required and the space available
  System::Int64Op $1 > $2
  Pop $3

  IntCmp $3 1 done
    MessageBox MB_OK "Error: Not enough disk space!"
    ${LogText} "ERROR: Not enough disk space!"
    Abort
done:
FunctionEnd


Function FreeDiskSpace
; $9 contains parent dir for installation
  System::Call 'Kernel32::GetDiskFreeSpaceEx(t "$9", *l.r1, *l.r2, *l.r3)i.r0'
  ${If} $0 <> 0
; convert byte values into KB
    System::Int64Op $1 / 1024
    Pop $1
  ${Else}
    ${LogText} "An error occurred during calculation disk space $0"
  ${EndIf}
FunctionEnd

;------------------------------------------------------------------------------
; custom uninstall functions
;------------------------------------------------------------------------------

Function un.getRegKey
  ReadRegStr $R2 HKCU "Software\${MANUFACTURER}\${PRODUCT_REG_VER}" ""
  StrCpy $R2 "$R2\bin"
  StrCmp $R2 $INSTDIR HKCU admin
HKCU:
  StrCpy $baseRegKey "HKCU"
  Goto Done
admin:
  ReadRegStr $R2 HKLM "Software\${MANUFACTURER}\${PRODUCT_REG_VER}" ""
  StrCpy $R2 "$R2\bin"
  StrCmp $R2 $INSTDIR HKLM cant_find_installation
HKLM:
  StrCpy $baseRegKey "HKLM"
  Goto Done

cant_find_installation:
; compare installdir with default user location
  ${UnStrStr} $R0 $INSTDIR $LOCALAPPDATA\${MANUFACTURER}
  StrCmp $R0 $INSTDIR HKCU 0

; compare installdir with default admin location
  ${If} ${RunningX64}
    ${UnStrStr} $R0 $INSTDIR $PROGRAMFILES64
    StrCmp $R0 $INSTDIR HKLM look_at_program_files_32
  ${Else}
look_at_program_files_32:
    ${UnStrStr} $R0 $INSTDIR $PROGRAMFILES
    StrCmp $R0 $INSTDIR HKCU undefined_location
  ${EndIf}

; installdir does not contain known default locations
undefined_location:
  Goto HKLM
Done:
FunctionEnd


Function un.onUninstSuccess
  SetErrorLevel 0
FunctionEnd


Function un.UninstallFeedback
; do not ask user about UNINSTALL FEEDBACK if uninstallation was run from another installation
  Push $R0
  Push $R1
  ${GetParameters} $R0
  ClearErrors
  ${GetOptions} $R0 /NO_UNINSTALL_FEEDBACK= $R1
  IfErrors done
  !insertmacro INSTALLOPTIONS_WRITE "DeleteSettings.ini" "Field 6" "State" "0"
done:
  Pop $R1
  Pop $R0
  ClearErrors
FunctionEnd


Function un.onInit
  !insertmacro INSTALLOPTIONS_EXTRACT "DeleteSettings.ini"
  Call un.UninstallFeedback

; Uninstallation was run from installation dir?
  IfFileExists "$INSTDIR\IdeaWin32.dll" 0 end_of_uninstall
  IfFileExists "$INSTDIR\IdeaWin64.dll" 0 end_of_uninstall
  IfFileExists "$INSTDIR\${PRODUCT_EXE_FILE_64}" 0 end_of_uninstall
  IfFileExists "$INSTDIR\${PRODUCT_EXE_FILE}" 0 end_of_uninstall

get_reg_key:
  SetRegView 32
  Call un.getRegKey
  StrCmp $baseRegKey "HKLM" uninstall_location UAC_Done

uninstall_location:
  ;check if the uninstallation is running from the product location
  IfFileExists $LOCALAPPDATA\${PRODUCT_PATHS_SELECTOR}_${VER_BUILD}_Uninstall.exe UAC_Elevate required_admin_perm

required_admin_perm:
  ;the user has admin rights?
  UserInfo::GetAccountType
  Pop $R2
  StrCmp $R2 "Admin" UAC_Admin copy_uninstall

copy_uninstall:
  ;do copy for unistall.exe
  CopyFiles "$OUTDIR\Uninstall.exe" "$LOCALAPPDATA\${PRODUCT_PATHS_SELECTOR}_${VER_BUILD}_Uninstall.exe"
  IfSilent uninstall_silent_mode uninstall_gui_mode

uninstall_silent_mode:
  ExecWait '"$LOCALAPPDATA\${PRODUCT_PATHS_SELECTOR}_${VER_BUILD}_Uninstall.exe" /S _?=$INSTDIR'
  Goto delete_uninstaller_itself
uninstall_gui_mode:
  ExecWait '"$LOCALAPPDATA\${PRODUCT_PATHS_SELECTOR}_${VER_BUILD}_Uninstall.exe" _?=$INSTDIR'

delete_uninstaller_itself:
  Delete "$LOCALAPPDATA\${PRODUCT_PATHS_SELECTOR}_${VER_BUILD}_Uninstall.exe"
  IfFileExists "$INSTDIR\bin\*.*" 0 delete_install_dir
  StrCpy $0 "$INSTDIR\bin"
  Call un.deleteDirIfEmpty
delete_install_dir:
  IfFileExists "$INSTDIR\*.*" 0 quit
  StrCpy $0 "$INSTDIR"
  Call un.deleteDirIfEmpty
quit:
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
  Goto UAC_Done
end_of_uninstall:
  MessageBox MB_OK|MB_ICONEXCLAMATION "$(uninstaller_relocated)"
  Abort
UAC_Done:
  !insertmacro MUI_UNGETLANGUAGE
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

;------------------------------------------------------------------------------
; custom uninstall pages
;------------------------------------------------------------------------------

Function un.ConfirmDeleteSettings
  !insertmacro MUI_HEADER_TEXT "$(uninstall_options)" "$(uninstall_options_prompt)"
  !insertmacro INSTALLOPTIONS_WRITE "DeleteSettings.ini" "Field 1" "Text" "$(prompt_delete_settings)"
  ${UnStrRep} $R1 $INSTDIR '\' '\\'
  !insertmacro INSTALLOPTIONS_WRITE "DeleteSettings.ini" "Field 2" "Text" $R1
  !insertmacro INSTALLOPTIONS_WRITE "DeleteSettings.ini" "Field 3" "Text" "$(text_delete_settings)"
  !insertmacro INSTALLOPTIONS_WRITE "DeleteSettings.ini" "Field 4" "Text" "$(confirm_delete_caches)"
  !insertmacro INSTALLOPTIONS_WRITE "DeleteSettings.ini" "Field 5" "Text" "$(confirm_delete_settings)"
  ;do not show feedback web page checkbox for EAP builds.
  StrCmp "${PRODUCT_WITH_VER}" "${MUI_PRODUCT} ${VER_BUILD}" hide_feedback_checkbox feedback_web_page
feedback_web_page:
  StrCmp "${UNINSTALL_WEB_PAGE}" "feedback_web_page" hide_feedback_checkbox done
hide_feedback_checkbox:
    ; do not show feedback web page checkbox through products uninstall.
    push $R1
    !insertmacro INSTALLOPTIONS_READ $R1 "DeleteSettings.ini" "Settings" "NumFields"
    IntOp $R1 $R1 - 1
    !insertmacro INSTALLOPTIONS_WRITE "DeleteSettings.ini" "Settings" "NumFields" "$R1"
    pop $R1
done:
  !insertmacro INSTALLOPTIONS_DISPLAY "DeleteSettings.ini"
FunctionEnd


Function un.PrepareCustomPath
  ;Input:
  ;$0 - name of variable
  ;$1 - value of the variable
  ;$2 - line from the property file
  push $3
  push $5
  ${UnStrLoc} $3 $2 $0 ">"
  StrCmp $3 "" not_found
  StrLen $5 $0
  IntOp $3 $3 + $5
  StrCpy $2 $2 "" $3
  IfFileExists "$1$2\\*.*" not_found
  StrCpy $2 $1$2
  goto complete
not_found:
  StrCpy $0 ""
complete:
  pop $5
  pop $3
FunctionEnd


Function un.getCustomPath
  push $0
  push $1
  StrCpy $0 "${user.home}/"
  StrCpy $1 "$PROFILE/"
  Call un.PrepareCustomPath
  StrCmp $0 "" check_idea_var
  goto complete
check_idea_var:
  StrCpy $0 "${idea.home}/"
  StrCpy $1 "$INSTDIR/"
  Call un.PrepareCustomPath
  StrCmp $2 "" +1 +2
  StrCpy $2 ""
complete:
  pop $1
  pop $0
FunctionEnd


Function un.getPath
; The function read lines from idea.properties and search the substring and prepare the path to settings or caches.
  ClearErrors
  FileOpen $3 $INSTDIR\bin\idea.properties r
  IfErrors complete ;file can not be open. not sure if a message should be displayed in this case.
  StrLen $5 $1
read_line:
  FileRead $3 $4
  StrCmp $4 "" complete
  ${UnStrLoc} $6 $4 $1 ">"
  StrCmp $6 "" read_line ; there is no substring in a string from the file. go for next one.
  IntOp $6 $6 + $5
  ${unStrStr} $7 $4 "#" ;check if the property has been customized
  StrCmp $7 "" custom
  StrCpy $2 "$PROFILE/${PRODUCT_SETTINGS_DIR}/$0" ;no. use the default value.
  goto complete
custom:
  StrCpy $2 $4 "" $6
  Call un.getCustomPath
complete:
  FileClose $3
  ${UnStrRep} $2 $2 "/" "\"
  DetailPrint "path to config/system: $2"
FunctionEnd

Function un.isIDEInUse
  IfFileExists $R0 0 done
  CopyFiles $R0 "$R0_copy"
  ClearErrors
  Delete $R0
  IfFileExists $R0 done
  CopyFiles "$R0_copy" $R0
done:
  Delete "$R0_copy"
FunctionEnd


Function un.checkIfIDEInUse
remove_previous_installation:
  StrCpy $R0 "$INSTDIR\IdeaWin32.dll"
  Call un.isIDEInUse
  IfErrors remove_dialog 0
  StrCpy $R0 "$INSTDIR\IdeaWin64.dll"
  Call un.isIDEInUse
  IfErrors remove_dialog done
remove_dialog:
  MessageBox MB_OKCANCEL|MB_ICONQUESTION|MB_TOPMOST "$(application_running)" IDOK remove_previous_installation IDCANCEL cancel
cancel:
  Abort
done:
FunctionEnd


Function un.validateStartMenuLinkToLauncher
;check if exists and compare with $INSTDIR
  ClearErrors
  StrCpy $8 ""
  ShellLink::GetShortCutWorkingDirectory $7
  Pop $0
  IfErrors done 0
  StrCmp $0 "$productDir" 0 incorrect_link
  StrCpy $8 $0
  goto done
incorrect_link:
  DetailPrint "The link ($7) does not exist or incorrect."
done:
  ClearErrors
FunctionEnd


Section "Uninstall"
  Call un.customUninstallActions
  SetRegView 32
  DetailPrint "baseRegKey: $baseRegKey"
  StrCpy $0 $baseRegKey
  StrCpy $1 "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_WITH_VER}"
  StrCpy $2 "InstallLocation"
  Call un.OMReadRegStr
  DetailPrint "uninstall location: $3"
  ;check if the uninstalled application is running
  Call un.checkIfIDEInUse
  ; Uninstaller is in the \bin directory, we need upper level dir
  StrCpy $productDir $INSTDIR
  StrCpy $INSTDIR $INSTDIR\..

  StrCpy $0 $baseRegKey
  StrCpy $1 "Software\${MANUFACTURER}\${PRODUCT_REG_VER}"
  StrCpy $2 "MenuFolder"
  call un.OMReadRegStr
  StrCmp $3 "" delete_caches shortcuts

shortcuts:
  StrCpy $7 "$SMPROGRAMS\$3\${PRODUCT_FULL_NAME_WITH_VER}.lnk"
  Call un.validateStartMenuLinkToLauncher
  StrCmp $8 "" 0 remove_link
  DetailPrint "StartMenu: $7 is not point to valid launcher."
  goto delete_caches

remove_link:
  Delete $7
  ; Delete only if empty (last IDEA version is uninstalled)
  RMDir  "$SMPROGRAMS\$3"

delete_caches:
  !insertmacro INSTALLOPTIONS_READ $R2 "DeleteSettings.ini" "Field 4" "State"
  DetailPrint "Data: $DOCUMENTS\..\${PRODUCT_SETTINGS_DIR}\"
  StrCmp $R2 1 0 skip_delete_caches
; find the path to caches (system) folder
   StrCpy $0 "system"
   StrCpy $1 "idea.system.path="
   Call un.getPath
   StrCmp $2 "" skip_delete_caches
   StrCpy $system_path $2
   RmDir /r "$system_path"
   RmDir "$system_path\\.." ; remove parent of system dir if the dir is empty

skip_delete_caches:
  !insertmacro INSTALLOPTIONS_READ $R3 "DeleteSettings.ini" "Field 5" "State"
  StrCmp $R3 1 "" skip_delete_settings
; find the path to settings (config) folder
    StrCpy $0 "config"
    StrCpy $1 "idea.config.path="
    Call un.getPath
    StrCmp $2 "" skip_delete_settings
    StrCpy $config_path $2
    RmDir /r "$config_path"
;    RmDir /r $DOCUMENTS\..\${PRODUCT_SETTINGS_DIR}\config
    Delete "$INSTDIR\bin\${PRODUCT_VM_OPTIONS_NAME}"
    Delete "$INSTDIR\bin\idea.properties"
    StrCmp $R2 1 "" skip_delete_settings
    RmDir "$config_path\\.." ; remove parent of config dir if the dir is empty
;    RmDir $DOCUMENTS\..\${PRODUCT_SETTINGS_DIR}

skip_delete_settings:
; Delete uninstaller itself
  Delete "$INSTDIR\bin\Uninstall.exe"
  Delete "$INSTDIR\jre64\bin\server\classes.jsa"

  Push "Complete"
  Push "$INSTDIR\bin\${PRODUCT_EXE_FILE}.vmoptions"
  Push "$INSTDIR\bin\idea.properties"
  ${UnStrRep} $0 ${PRODUCT_EXE_FILE} ".exe" "64.exe.vmoptions"
  Push "$INSTDIR\bin\$0"
  Call un.compareFileInstallationTime
  ${If} $9 != "Modified"
    Delete "$INSTDIR\bin\idea.properties"
    Delete "$INSTDIR\bin\${PRODUCT_EXE_FILE}.vmoptions"
    Delete "$INSTDIR\bin\${PRODUCT_EXE_FILE_64}.vmoptions"
  ${EndIf}
  IfFileExists "$INSTDIR\jre32\*.*" 0 no_jre32
    Delete "$INSTDIR\jre32\bin\server\classes.jsa"
    StrCpy $0 "$INSTDIR\jre32\lib\applet"
    Call un.deleteDirIfEmpty
no_jre32:
  !include "unidea_win.nsh"
  StrCpy $0 "$INSTDIR\bin"
  Call un.deleteDirIfEmpty
  StrCpy $0 "$INSTDIR"
  Call un.deleteDirIfEmpty

; remove desktop shortcuts
desktop_shortcut_launcher32:
  IfFileExists "$DESKTOP\${PRODUCT_FULL_NAME_WITH_VER}.lnk" 0 desktop_shortcut_launcher64
    DetailPrint "remove desktop shortcut to launcher32: $DESKTOP\${PRODUCT_FULL_NAME_WITH_VER}.lnk"
    Delete "$DESKTOP\${PRODUCT_FULL_NAME_WITH_VER}.lnk"
desktop_shortcut_launcher64:
  IfFileExists "$DESKTOP\${PRODUCT_FULL_NAME_WITH_VER} x64.lnk" 0 registry
    DetailPrint "remove desktop shortcut to launcher64: $DESKTOP\${PRODUCT_FULL_NAME_WITH_VER} x64.lnk"
    Delete "$DESKTOP\${PRODUCT_FULL_NAME_WITH_VER} x64.lnk"

registry:
  StrCpy $0 "SHCTX"
  StrCpy $1 "Software\Classes\*\shell\Open with ${MUI_PRODUCT}"
  call un.OMDeleteRegKey

  StrCpy $1 "Software\Classes\Directory\shell\${MUI_PRODUCT}"
  call un.OMDeleteRegKey

  StrCpy $1 "Software\Classes\Directory\Background\shell\${MUI_PRODUCT}"
  call un.OMDeleteRegKey

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

  StrCpy $0 "HKCR"
  StrCpy $1 "IntelliJIdeaProjectFile\DefaultIcon"
  StrCpy $2 ""
  call un.OMReadRegStr

  StrCmp $3 "$productDir\${PRODUCT_EXE_FILE},0" remove_IntelliJIdeaProjectFile done
remove_IntelliJIdeaProjectFile:
  StrCpy $1 "IntelliJIdeaProjectFile"
  Call un.OMDeleteRegKey
done:
  StrCpy $0 $baseRegKey
  StrCpy $1 "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_WITH_VER}"
  Call un.OMDeleteRegKey
  ;do not show feedback web page checkbox for EAP builds.
  StrCmp "${PRODUCT_WITH_VER}" "${MUI_PRODUCT} ${VER_BUILD}" end_of_uninstall feedback_web_page
feedback_web_page:
  StrCmp "${UNINSTALL_WEB_PAGE}" "feedback_web_page" end_of_uninstall
  !insertmacro INSTALLOPTIONS_READ $R3 "DeleteSettings.ini" "Field 6" "State"
  StrCmp "$R3" "0" end_of_uninstall
  ExecShell "" "${UNINSTALL_WEB_PAGE}"
end_of_uninstall:
SectionEnd
