!verbose 2

Unicode true
ManifestDPIAware true
!addplugindir "${NSIS_DIR}\Plugins\x86-unicode"
!addincludedir "${NSIS_DIR}\Include"

!include "paths.nsi"
!include "strings.nsi"
!include "Registry.nsi"
!include "version.nsi"
!include WinVer.nsh
!include x64.nsh
!define JAVA_REQUIREMENT 1.8

; Product with version (IntelliJ IDEA #xxxx).

; Used in registry to put each build info into the separate subkey
; Add&Remove programs doesn't understand subkeys in the Uninstall key,
; thus ${PRODUCT_WITH_VER} is used for uninstall registry information
!define PRODUCT_REG_VER "${MUI_PRODUCT}\${VER_BUILD}"

Name "${MUI_PRODUCT}"
SetCompressor lzma
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
Var control_fields
Var max_fields
Var silentMode

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
${UnStrLoc}
${UnStrRep}
${StrRep}

!include "customInstallActions.nsi"

ReserveFile "desktop.ini"
ReserveFile "DeleteSettings.ini"
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
!define MUI_LANGDLL_REGISTRY_ROOT "HKCU"
!define MUI_LANGDLL_REGISTRY_KEY "Software\JetBrains\${MUI_PRODUCT}\${VER_BUILD}\"
!define MUI_LANGDLL_REGISTRY_VALUENAME "Installer Language"


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
  FindClose $1
  Pop $2
  Pop $1
  Pop $0
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
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 2" "Text" $R0

  ${If} $R1 != ""
    !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 3" "Type" "checkbox"
    !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 3" "Text" $R1
  ${EndIf}

  ; if jre x86 for the build is available then add checkbox to Installation Options dialog
  StrCmp "${LINK_TO_JRE}" "null" custom_pre_actions 0
  inetc::head /SILENT /TOSTACK ${LINK_TO_JRE} "" /END
  Pop $0
  ${If} $0 == "OK"
    ; download jre x86: optional if OS is not 32-bit
    ${If} ${RunningX64}
      StrCpy $downloadJreX86 "0"
    ${Else}
      StrCpy $downloadJreX86 "1"
    ${EndIf}
    !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 4" "Type" "checkbox"
    !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 4" "State" $downloadJreX86
    !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 4" "Text" "Download and install JRE x86 by JetBrains"
  ${EndIf}
custom_pre_actions:
  Call customPreInstallActions
  SetRegView 32
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


Function downloadJre
  !insertmacro INSTALLOPTIONS_READ $R0 "Desktop.ini" "Field 4" "State"
  ${If} $R0 == 1
    inetc::get ${LINK_TO_JRE} "$TEMP\jre.tar.gz" /END
    Pop $0
    ${If} $0 == "OK"
      untgz::extract "-d" "$INSTDIR\jre32" "$TEMP\jre.tar.gz"
      StrCmp $R0 "success" remove_temp_jre
      DetailPrint "Failed to extract jre.tar.gz"
      MessageBox MB_OK|MB_ICONEXCLAMATION|MB_DEFBUTTON1 "Failed to extract $TEMP\jre.tar.gz"
remove_temp_jre:
      IfFileExists "$TEMP\jre.tar.gz" 0 done
      Delete "$TEMP\jre.tar.gz"
    ${Else}
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
!insertmacro MUI_PAGE_DIRECTORY

Page custom ConfirmDesktopShortcut
  !define MUI_STARTMENUPAGE_NODISABLE
  !define MUI_STARTMENUPAGE_DEFAULTFOLDER "JetBrains"

!insertmacro MUI_PAGE_STARTMENU Application $STARTMENU_FOLDER
!define MUI_ABORTWARNING
!insertmacro MUI_PAGE_INSTFILES
!define MUI_FINISHPAGE_RUN_NOTCHECKED
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


Function .onInit
  SetRegView 32
  !insertmacro INSTALLOPTIONS_EXTRACT "Desktop.ini"
  IfSilent silent_mode uac_elevate
silent_mode:
  IntCmp ${CUSTOM_SILENT_CONFIG} 0 silent_config silent_config custom_silent_config
silent_config:
  Call silentConfigReader
  Goto set_reg_key
custom_silent_config:
  Call customSilentConfigReader
set_reg_key:
  StrCpy $baseRegKey "HKCU"
  StrCmp $silentMode "admin" uac_elevate done
uac_elevate:
  !insertmacro UAC_RunElevated
  StrCmp 1223 $0 uac_elevation_aborted ; UAC dialog aborted by user? - continue install under user
  StrCmp 0 $0 0 uac_err ; Error?
  StrCmp 1 $1 0 uac_success ;Are we the real deal or just the wrapper?
  Quit
uac_err:
  Abort
uac_elevation_aborted:
  IfSilent done set_install_dir
set_install_dir:
  StrCpy $INSTDIR "$APPDATA\${MANUFACTURER}\${PRODUCT_WITH_VER}"
  goto done
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
done:
;  !insertmacro MUI_LANGDLL_DISPLAY
FunctionEnd


Function silentConfigReader
  ${GetParameters} $R0
  ClearErrors

  ${GetOptions} $R0 /CONFIG= $R1
  IfErrors no_silent_config

  ${ConfigRead} "$R1" "mode=" $R0
  StrCpy $silentMode "user"
  IfErrors launcher_32
  StrCpy $silentMode $R0

launcher_32:
  ClearErrors
  ${ConfigRead} "$R1" "launcher32=" $R3
  IfErrors launcher_64
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 2" "State" $R3

launcher_64:
  ClearErrors
  ${ConfigRead} "$R1" "launcher64=" $R3
  IfErrors download_jre32
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 3" "Type" "checkbox"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 3" "State" $R3

download_jre32:
  ClearErrors
  ${ConfigRead} "$R1" "jre32=" $R3
  IfErrors associations
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 4" "Type" "checkbox"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 4" "State" $R3

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
  goto loop

update_settings:
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Settings" "NumFields" "$R0"
no_silent_config:
done:
FunctionEnd

Function checkVersion
  StrCpy $2 ""
  StrCpy $1 "Software\${MANUFACTURER}\${PRODUCT_REG_VER}"
  Call OMReadRegStr
  IfFileExists $3\bin\${PRODUCT_EXE_FILE} check_version
  Goto Done
check_version:
  StrCpy $2 "Build"
  Call OMReadRegStr
  StrCmp $3 "" Done
  IntCmpU $3 ${VER_BUILD} ask_Install_Over Done ask_Install_Over
ask_Install_Over:
  MessageBox MB_YESNO|MB_ICONQUESTION "$(current_version_already_installed)" IDYES continue IDNO exit_installer
exit_installer:
  Abort
continue:
  StrCpy $0 "complete"
Done:
FunctionEnd


Function searchCurrentVersion
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
  ${If} $9 == "1"
    ExecWait '"$3\bin\Uninstall.exe" /S'
  ${else}
    ExecWait '"$3\bin\Uninstall.exe" _?=$3\bin'
  ${EndIf}
  IfFileExists $3\bin\${PRODUCT_EXE_FILE} 0 uninstall
  goto complete
uninstall:
  ;previous installation has been removed
  ;customer has decided to keep properties?
  IfFileExists $3\bin\idea.properties saveProperties fullRemove
saveProperties:
  Delete "$3\bin\Uninstall.exe"
  Goto complete
fullRemove:
  RmDir /r "$3"
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


Function uninstallOldVersionDialog
  StrCpy $control_fields 2
  StrCpy $max_fields 13
  StrCpy $0 "HKLM"
  StrCpy $4 0
  ReserveFile "UninstallOldVersions.ini"
  !insertmacro INSTALLOPTIONS_EXTRACT "UninstallOldVersions.ini"
  StrCpy $8 $control_fields

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
    !insertmacro INSTALLOPTIONS_WRITE "UninstallOldVersions.ini" "Field $8" "Text" "$3"
    StrCmp $8 $max_fields complete
  ${EndIf}
get_next_key:
  IntOp $4 $4 + 1 ;to check next record from registry
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
  !insertmacro INSTALLOPTIONS_DISPLAY "UninstallOldVersions.ini"
  ;uninstall chosen installation(s)

  ;no disabled controls. StrCmp $2 "OK" loop finish
loop:
  !insertmacro INSTALLOPTIONS_READ $0 "UninstallOldVersions.ini" "Field $8" "State"
  !insertmacro INSTALLOPTIONS_READ $3 "UninstallOldVersions.ini" "Field $8" "Text"
  ${If} $0 == "1"
    Call uninstallOldVersion
    ${EndIf}
    IntOp $8 $8 - 1
    StrCmp $8 $control_fields finish loop
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
  StrCpy $3 "0"        # latest build number
  StrCpy $0 "0"        # registry key index

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

Function ProductAssociation
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
  DetailPrint "productLauncher: $productLauncher"

  StrCmp "${LINK_TO_JRE}" "null" shortcuts 0
  ;download and install JRE x86
  Call downloadJre

shortcuts:
  !insertmacro INSTALLOPTIONS_READ $R2 "Desktop.ini" "Field 2" "State"
  StrCmp $R2 1 "" exe_64
  CreateShortCut "$DESKTOP\${PRODUCT_FULL_NAME_WITH_VER}.lnk" \
                 "$INSTDIR\bin\${PRODUCT_EXE_FILE}" "" "" "" SW_SHOWNORMAL
exe_64:
  !insertmacro INSTALLOPTIONS_READ $R2 "Desktop.ini" "Field 3" "State"
  StrCmp $R2 1 "" skip_desktop_shortcut
  CreateShortCut "$DESKTOP\${PRODUCT_FULL_NAME_WITH_VER} x64.lnk" \
                 "$INSTDIR\bin\${PRODUCT_EXE_FILE_64}" "" "" "" SW_SHOWNORMAL

skip_desktop_shortcut:
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
  IfFileExists $INSTDIR\jre32\bin\javaw.exe 0 java64
  ExecWait "$INSTDIR\jre32\bin\javaw.exe -Xshare:dump"
java64:
  IfFileExists $INSTDIR\jre64\bin\javaw.exe 0 skip_regeneration_shared_archive_for_java_64
  ExecWait "$INSTDIR\jre64\bin\javaw.exe -Xshare:dump"

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
  System::Call 'shell32.dll::SHChangeNotify(i, i, i, i) v (0x08000000, 0, 0, 0)'
SectionEnd

;------------------------------------------------------------------------------
; custom uninstall functions
;------------------------------------------------------------------------------

Function un.getRegKey
  ReadRegStr $R2 HKCU "Software\${MANUFACTURER}\${PRODUCT_REG_VER}" ""
  StrCpy $R2 "$R2\bin"
  StrCmp $R2 $INSTDIR HKCU admin
HKCU:
  StrCpy $baseRegKey "HKCU"
  goto Done
admin:
  ReadRegStr $R2 HKLM "Software\${MANUFACTURER}\${PRODUCT_REG_VER}" ""
  StrCpy $R2 "$R2\bin"
  StrCmp $R2 $INSTDIR HKLM cant_find_installation
HKLM:
  StrCpy $baseRegKey "HKLM"
  goto Done
cant_find_installation:
  ;admin perm. is required to uninstall?
  ${If} ${RunningX64}
look_at_program_files_64:
    ${UnStrStr} $R0 $INSTDIR $PROGRAMFILES64
    StrCmp $R0 $INSTDIR HKLM look_at_program_files_32
  ${Else}
look_at_program_files_32:
    ${UnStrStr} $R0 $INSTDIR $PROGRAMFILES
    StrCmp $R0 $INSTDIR HKCU uninstaller_relocated
  ${EndIf}
uninstaller_relocated:
    MessageBox MB_OK|MB_ICONEXCLAMATION "$(uninstaller_relocated)"
    Abort
Done:
FunctionEnd


Function un.onInit
  SetRegView 32
  Call un.getRegKey
  StrCmp $baseRegKey "HKLM" required_admin_perm UAC_Done

required_admin_perm:
  ;the user has admin rights?
  UserInfo::GetAccountType
  Pop $R2
  StrCmp $R2 "Admin" UAC_Admin uninstall_location

uninstall_location:
  ;check if the uninstallation is running from the product location
  IfFileExists $APPDATA\${PRODUCT_PATHS_SELECTOR}_${VER_BUILD}_Uninstall.exe UAC_Elevate copy_uninstall

copy_uninstall:
  ;do copy for unistall.exe
  CopyFiles "$OUTDIR\Uninstall.exe" "$APPDATA\${PRODUCT_PATHS_SELECTOR}_${VER_BUILD}_Uninstall.exe"
  ExecWait '"$APPDATA\${PRODUCT_PATHS_SELECTOR}_${VER_BUILD}_Uninstall.exe" _?=$INSTDIR'
  Delete "$APPDATA\${PRODUCT_PATHS_SELECTOR}_${VER_BUILD}_Uninstall.exe"
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
UAC_Done:
  !insertmacro MUI_UNGETLANGUAGE
  !insertmacro INSTALLOPTIONS_EXTRACT "DeleteSettings.ini"
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
  Delete $R0"
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
FunctionEnd


Section "Uninstall"
  Call un.customUninstallActions
  SetRegView 32
  StrCpy $0 $baseRegKey
  StrCpy $1 "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_WITH_VER}"
  StrCpy $2 "InstallLocation"
  Call un.OMReadRegStr
  StrCmp $INSTDIR "$3\bin" check_if_IDE_in_use invalid_installation_dir
invalid_installation_dir:
  ;check if uninstaller runs from not installation folder
  IfFileExists "$INSTDIR\IdeaWin32.dll" 0 end_of_uninstall
  IfFileExists "$INSTDIR\IdeaWin64.dll" 0 end_of_uninstall
  IfFileExists "$INSTDIR\${PRODUCT_EXE_FILE_64}" 0 end_of_uninstall
  IfFileExists "$INSTDIR\${PRODUCT_EXE_FILE}" check_if_IDE_in_use 0
  goto end_of_uninstall
check_if_IDE_in_use:
  ;check if the uninstalled application is running
  Call un.checkIfIDEInUse
  ; Uninstaller is in the \bin directory, we need upper level dir
  StrCpy $productDir $INSTDIR
  StrCpy $INSTDIR $INSTDIR\..

  ReadRegStr $R9 HKCU "Software\${MANUFACTURER}\${PRODUCT_REG_VER}" "MenuFolder"
  StrCmp $R9 "" "" shortcuts
  ReadRegStr $R9 HKLM "Software\${MANUFACTURER}\${PRODUCT_REG_VER}" "MenuFolder"
  StrCmp $R9 "" delete_caches
  StrCpy $5 "Software\${MANUFACTURER}"

shortcuts:
  ;user does not have the admin rights
  SetShellVarContext current
  StrCpy $7 "$SMPROGRAMS\$R9\${PRODUCT_FULL_NAME_WITH_VER}.lnk"
  ;check is exists and compare with $INSTDIR
  Call un.validateStartMenuLinkToLauncher
  StrCmp $8 "" 0 keep_current_user
  ;  IfFileExists "$SMPROGRAMS\$R9\${PRODUCT_FULL_NAME_WITH_VER}.lnk" keep_current_user

  ;user has the admin rights
  SetShellVarContext all
  StrCpy $7 "$SMPROGRAMS\$R9\${PRODUCT_FULL_NAME_WITH_VER}.lnk"
  DetailPrint "7, admin: $7"
  Call un.validateStartMenuLinkToLauncher
  StrCmp $8 "" 0 keep_current_user
  DetailPrint "StartMenu: $7 is not point to valid launcher."
  goto delete_caches

keep_current_user:
  Delete $7
  ; Delete only if empty (last IDEA version is uninstalled)
  RMDir  "$SMPROGRAMS\$R9"

delete_caches:
  !insertmacro INSTALLOPTIONS_READ $R2 "DeleteSettings.ini" "Field 4" "State"
  DetailPrint "Data: $DOCUMENTS\..\${PRODUCT_SETTINGS_DIR}\"
  StrCmp $R2 1 "" skip_delete_caches
   ;find the path to caches (system) folder
   StrCpy $0 "system"
   StrCpy $1 "idea.system.path="
   Call un.getPath
   StrCmp $2 "" skip_delete_caches
   StrCpy $system_path $2
   RmDir /r "$system_path"
   RmDir "$system_path\\.." ; remove parent of system dir if the dir is empty
;   RmDir /r $DOCUMENTS\..\${PRODUCT_SETTINGS_DIR}\system
skip_delete_caches:

  !insertmacro INSTALLOPTIONS_READ $R3 "DeleteSettings.ini" "Field 5" "State"
  StrCmp $R3 1 "" skip_delete_settings
    ;find the path to settings (config) folder
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
  Delete "$INSTDIR\jre32\bin\client\classes.jsa"

  Push "Complete"
  Push "$INSTDIR\bin\${PRODUCT_EXE_FILE}.vmoptions"
  Push "$INSTDIR\bin\idea.properties"
  ${UnStrRep} $0 ${PRODUCT_EXE_FILE} ".exe" "64.exe.vmoptions"
  Push "$INSTDIR\bin\$0"
  Call un.compareFileInstallationTime
  ${If} $9 != "Modified"
    RMDir /r "$INSTDIR"
  ${Else}
    !include "unidea_win.nsh"
    RMDir "$INSTDIR"
  ${EndIf}

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
  StrCmp $baseRegKey "HKLM" admin user
admin:
  StrCpy $0 "HKCR"
  StrCpy $1 "${PRODUCT_PATHS_SELECTOR}"
  goto delete_association
user:
  StrCpy $0 "HKCU"
  StrCpy $1 "Software\Classes\${PRODUCT_PATHS_SELECTOR}"
delete_association:
  ; remove product information which was used for association(s)
  Call un.OMDeleteRegKey

  StrCpy $0 "${MUI_LANGDLL_REGISTRY_ROOT}"
  StrCpy $1 "${MUI_LANGDLL_REGISTRY_KEY}"
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
