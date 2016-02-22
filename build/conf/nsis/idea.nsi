!verbose 2

!include "paths.nsi"
!include "strings.nsi"
!include "Registry.nsi"
!include "version.nsi"

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
; include "Modern User Interface"
;------------------------------------------------------------------------------
!include "MUI2.nsh"
!include "FileFunc.nsh"
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
ReserveFile '${NSISDIR}\Plugins\InstallOptions.dll'
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

Var baseRegKey
Var IS_UPGRADE_60

!define MUI_LANGDLL_REGISTRY_ROOT "HKCU"
!define MUI_LANGDLL_REGISTRY_KEY "Software\JetBrains\${MUI_PRODUCT}\${VER_BUILD}\"
!define MUI_LANGDLL_REGISTRY_VALUENAME "Installer Language"


!macro INST_UNINST_SWITCH un
  ;check if the window is win7 or newer
  Function ${un}winVersion
    ;The platform is returned into $0, minor version into $1.
    ;Windows 7 is equals values of 6 as platform and 1 as minor version.
    ;Windows 8 is equals values of 6 as platform and 2 as minor version.
    nsisos::osversion
    ${If} $0 == "6"
      ${AndIf} $1 >= "1"
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
    StrCpy $IS_UPGRADE_60 "0"
    ${InstDirState} "$INSTDIR" $R0
    IntCmp $R0 1 check_build skip_abort skip_abort
check_build:
    FileOpen $R1 "$INSTDIR\build.txt" "r"
    IfErrors do_abort
    FileRead $R1 $R2
    FileClose $R1
    IfErrors do_abort
    ${VersionSplit} ${MIN_UPGRADE_BUILD} $R3 $R4 $R5
    ${VersionSplit} ${MAX_UPGRADE_BUILD} $R6 $R7 $R8
    ${VersionSplit} $R2 $R9 $R2 $R0
    StrCmp $R9 $R3 0 do_abort
    IntCmp $R2 $R4 0 do_abort
    IntCmp $R0 $R5 do_accept do_abort

    StrCmp $R9 $R6 0 do_abort
    IntCmp $R2 $R7 0 0 do_abort
    IntCmp $R0 $R8 do_abort do_accept do_abort

do_accept:
    StrCpy $IS_UPGRADE_60 "1"
    FileClose $R1
    Goto skip_abort

do_abort:
  ;check
  ; - if there are no files into $INSTDIR (recursively) just excepted property files
  ; - if property files have the same installation time.
  StrCpy $9 "$INSTDIR"
  Call instDirEmpty
  StrCmp $9 "not empty" abort 0
  Push "Complete"
  Push "$INSTDIR\bin\${PRODUCT_EXE_FILE}.vmoptions"
  Push "$INSTDIR\bin\idea.properties"
  ${StrRep} $0 ${PRODUCT_EXE_FILE} ".exe" "64.exe.vmoptions"
  Push "$INSTDIR\bin\$0"
  Call compareFileInstallationTime
  StrCmp $9 "Modified" abort skip_abort
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
nextElemement:
  ;is the element a folder?
  StrCmp $2 "." getNextElement
  StrCmp $2 ".." getNextElement
  IfFileExists "$9\$2\*.*" 0 nextFile
    Push $9
    StrCpy "$9" "$9\$2"
    Call instDirEmpty
    StrCmp $9 "not empty" done 0
    Pop $9
    Goto getNextElement
nextFile:
  ;is it the file property?
  ${If} $2 != "idea.properties"
    ${AndIf} $2 != "${PRODUCT_EXE_FILE}.vmoptions"
      ${StrRep} $0 ${PRODUCT_EXE_FILE} ".exe" "64.exe.vmoptions"
      ${AndIf} $2 != $0
        StrCpy $9 "not empty"
        Goto done
  ${EndIf}
getNextElement:
  FindNext $1 $2
  IfErrors 0 nextElemement
done:
  FindClose $1
  Pop $2
  Pop $1
  Pop $0
FunctionEnd


;------------------------------------------------------------------------------
; Variables
;------------------------------------------------------------------------------
  Var STARTMENU_FOLDER
  Var config_path
  Var system_path

;------------------------------------------------------------------------------
; configuration
;------------------------------------------------------------------------------

!insertmacro MUI_PAGE_WELCOME

Page custom uninstallOldVersionDialog

Var productDir
Var control_fields
Var max_fields

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
!insertmacro UAC_AsUser_ExecShell "" "$INSTDIR\bin\${PRODUCT_EXE_FILE}" "" "" ""
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
  StrCpy $baseRegKey "HKCU"
  IfSilent UAC_Done
UAC_Elevate:
    !insertmacro UAC_RunElevated
    StrCmp 1223 $0 UAC_ElevationAborted ; UAC dialog aborted by user? - continue install under user
    StrCmp 0 $0 0 UAC_Err ; Error?
    StrCmp 1 $1 0 UAC_Success ;Are we the real deal or just the wrapper?
    Quit
UAC_Err:
    Abort
UAC_ElevationAborted:
    StrCpy $INSTDIR "$APPDATA\${MANUFACTURER}\${PRODUCT_WITH_VER}"
    goto UAC_Done
UAC_Success:
    StrCmp 1 $3 UAC_Admin ;Admin?
    StrCmp 3 $1 0 UAC_ElevationAborted ;Try again?
    goto UAC_Elevate
UAC_Admin:
    StrCpy $INSTDIR "$PROGRAMFILES\${MANUFACTURER}\${PRODUCT_WITH_VER}"
    SetShellVarContext all
    StrCpy $baseRegKey "HKLM"
UAC_Done:
;  !insertmacro MUI_LANGDLL_DISPLAY
FunctionEnd

Function checkVersion
  StrCpy $2 ""
  StrCpy $1 "Software\${MANUFACTURER}\${PRODUCT_REG_VER}"
;  ${If} $0 == "HKLM"
;    StrCpy $1 "Software\${MANUFACTURER}\${PRODUCT_REG_VER}"
;    Push $0
;    call winVersion
;    ${If} $0 == "1"
;      StrCpy $1 "Software\Wow6432Node\${MANUFACTURER}\${PRODUCT_REG_VER}"
;    ${Else}
;      StrCpy $1 "Software\${MANUFACTURER}\${PRODUCT_REG_VER}"
;    ${EndIf}
;    Pop $0
;  ${EndIf}
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
  ;check if the uninstalled application is running
remove_previous_installation:
  ;prepare a copy of launcher
  CopyFiles "$3\bin\${PRODUCT_EXE_FILE}" "$3\bin\${PRODUCT_EXE_FILE}_copy"
  ClearErrors
  ;copy launcher to itself
  CopyFiles "$3\bin\${PRODUCT_EXE_FILE}_copy" "$3\bin\${PRODUCT_EXE_FILE}"
  Delete "$3\bin\${PRODUCT_EXE_FILE}_copy"
  IfErrors 0 +3
  MessageBox MB_OKCANCEL|MB_ICONQUESTION|MB_TOPMOST "$(application_running)" IDOK remove_previous_installation IDCANCEL complete
  goto complete
  ; uninstallation mode
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
  ;customer decided to keep properties?
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

; search old versions of IDEA
  StrCpy $4 0
  StrCpy $0 "HKCU"
  StrCpy $1 "Software\${MANUFACTURER}\${MUI_PRODUCT}"
  StrCpy $5 "\bin\${PRODUCT_EXE_FILE}"
  StrCpy $2 ""
  Call getInstallationPath
  StrCmp $3 "complete" all_users
  IfFileExists $3\bin\${PRODUCT_EXE_FILE} old_version_located all_users
all_users:
  StrCpy $4 0
  StrCpy $0 "HKLM"
  Call getInstallationPath
  StrCmp $3 "complete" success
  IfFileExists $3\bin\${PRODUCT_EXE_FILE} 0 success
old_version_located:
;  MessageBox MB_YESNO|MB_ICONQUESTION "$(previous_installations)" IDYES uninstall IDNO success
;uninstall:
;  Call uninstallOldVersions

success:
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
  StrCpy $INSTDIR "$PROGRAMFILES\${MANUFACTURER}\${MUI_PRODUCT} ${MUI_VERSION_MAJOR}.${MUI_VERSION_MINOR}"
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
  StrCpy $3 '$INSTDIR\bin\${PRODUCT_EXE_FILE} "%1"'
  call OMWriteRegStr
FunctionEnd

Function ProductAssociation
 ; back up old value of an association
 ReadRegStr $1 HKCR $R4 ""
  StrCmp $1 "" skip_backup
    StrCmp $1 ${PRODUCT_PATHS_SELECTOR} skip_backup
    WriteRegStr HKCR $R4 "backup_val" $1
skip_backup:
  WriteRegStr HKCR $R4 "" "${PRODUCT_PATHS_SELECTOR}"
  ReadRegStr $0 HKCR ${PRODUCT_PATHS_SELECTOR} ""
  StrCmp $0 "" 0 command_exists
	WriteRegStr HKCR ${PRODUCT_PATHS_SELECTOR} "" "${PRODUCT_FULL_NAME}"
	WriteRegStr HKCR "${PRODUCT_PATHS_SELECTOR}\shell" "" "open"
	WriteRegStr HKCR "${PRODUCT_PATHS_SELECTOR}\DefaultIcon" "" "$INSTDIR\bin\${PRODUCT_EXE_FILE},0"
command_exists:
 WriteRegStr HKCR "${PRODUCT_PATHS_SELECTOR}\DefaultIcon" "" " $INSTDIR\bin\${PRODUCT_EXE_FILE},0"
 WriteRegStr HKCR "${PRODUCT_PATHS_SELECTOR}\shell\open\command" "" \
                  '$INSTDIR\bin\${PRODUCT_EXE_FILE} "%1"'
FunctionEnd

;------------------------------------------------------------------------------
; Installer sections
;------------------------------------------------------------------------------
Section "IDEA Files" CopyIdeaFiles

; create shortcuts
  !insertmacro INSTALLOPTIONS_READ $R2 "Desktop.ini" "Field 2" "State"
  StrCmp $R2 1 "" exe_64
  CreateShortCut "$DESKTOP\${PRODUCT_FULL_NAME_WITH_VER}.lnk" \
                 "$INSTDIR\bin\${PRODUCT_EXE_FILE}" "" "" "" SW_SHOWNORMAL
exe_64:
  !insertmacro INSTALLOPTIONS_READ $R2 "Desktop.ini" "Field 3" "State"
  StrCmp $R2 1 "" skip_desktop_shortcut
  CreateShortCut "$DESKTOP\${PRODUCT_FULL_NAME_WITH_VER}(64).lnk" \
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

  Call customInstallActions

  ;registration application to be presented in Open With list
  call ProductRegistration
  ;reset icon cache
  System::Call 'shell32.dll::SHChangeNotify(i, i, i, i) v (0x08000000, 0, 0, 0)'
!insertmacro MUI_STARTMENU_WRITE_BEGIN Application
; $STARTMENU_FOLDER stores name of IDEA folder in Start Menu,
; save it name in the "MenuFolder" RegValue
  CreateDirectory "$SMPROGRAMS\$STARTMENU_FOLDER"

  CreateShortCut "$SMPROGRAMS\$STARTMENU_FOLDER\${PRODUCT_FULL_NAME_WITH_VER}.lnk" \
                 "$INSTDIR\bin\${PRODUCT_EXE_FILE}" "" "" "" SW_SHOWNORMAL
;  CreateShortCut "$SMPROGRAMS\$STARTMENU_FOLDER\Uninstall ${PRODUCT_FULL_NAME_WITH_VER}.lnk" \
;                 "$INSTDIR\bin\Uninstall.exe"
  StrCpy $0 $baseRegKey
  StrCpy $1 "Software\${MANUFACTURER}\${PRODUCT_REG_VER}"
  StrCpy $2 "MenuFolder"
  StrCpy $3 "$STARTMENU_FOLDER"
  Call OMWriteRegStr
!insertmacro MUI_STARTMENU_WRITE_END

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
  WriteRegStr HKCR "IntelliJIdeaProjectFile\DefaultIcon" "" "$INSTDIR\bin\${PRODUCT_EXE_FILE},0"
  WriteRegStr HKCR "IntelliJIdeaProjectFile\shell\open\command" "" \
    '$INSTDIR\bin\${PRODUCT_EXE_FILE} "%1"'
!undef Index

skip_ipr:

; readonly section
  SectionIn RO
!include "idea_win.nsh"

  IntCmp $IS_UPGRADE_60 1 skip_properties
  SetOutPath $INSTDIR\bin
  File "${PRODUCT_PROPERTIES_FILE}"
  File "${PRODUCT_VM_OPTIONS_FILE}"
skip_properties:

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
              "DisplayIcon" "$INSTDIR\bin\${PRODUCT_EXE_FILE}"
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

  ExecWait "$INSTDIR\jre\jre\bin\javaw.exe -Xshare:dump"
  SetOutPath $INSTDIR\bin
  ; set the current time for installation files under $INSTDIR\bin
  ExecCmd::exec 'copy "$INSTDIR\bin\*.*s" +,,'
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
SectionEnd

;------------------------------------------------------------------------------
; custom uninstall functions
;------------------------------------------------------------------------------

Function un.getRegKey
  ReadRegStr $R2 HKCU "Software\${MANUFACTURER}\${PRODUCT_REG_VER}" ""
  StrCpy $R2 "$R2\bin"
user:
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
  ${UnStrStr} $R0 $INSTDIR $PROGRAMFILES
  StrCmp $R0 $INSTDIR HKLM HKCU
Done:
FunctionEnd


Function un.onInit
  Call un.getRegKey
  StrCmp $baseRegKey "HKLM" requred_admin_perm UAC_Done

requred_admin_perm:
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


Function un.ReturnBackupRegValue
  ;replace Default str with the backup value (if there is the one) and then delete backup
  ; $1 - key (for example ".java")
  ; $2 - name (for example "backup_val")
  Push $0
  ReadRegStr $0 HKCR $1 $2
  StrCmp $0 "" "noBackup"
    WriteRegStr HKCR $1 "" $0
    DeleteRegValue HKCR $1 $2
noBackup:
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
FunctionEnd


Section "Uninstall"
  ; Uninstaller is in the \bin directory, we need upper level dir
  StrCpy $productDir $INSTDIR
  StrCpy $INSTDIR $INSTDIR\..

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
  Delete "$INSTDIR\jre\jre\bin\client\classes.jsa"

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

  ReadRegStr $R9 HKCU "Software\${MANUFACTURER}\${PRODUCT_REG_VER}" "MenuFolder"
  StrCmp $R9 "" "" shortcuts
  ReadRegStr $R9 HKLM "Software\${MANUFACTURER}\${PRODUCT_REG_VER}" "MenuFolder"
  StrCmp $R9 "" registry
  StrCpy $5 "Software\${MANUFACTURER}"
shortcuts:
  ;the user has the admin rights
  IfFileExists "$DESKTOP\${PRODUCT_FULL_NAME_WITH_VER}.lnk" keep_current_user
  SetShellVarContext all
keep_current_user:
  DetailPrint "Start Menu: $SMPROGRAMS\$R9\${PRODUCT_FULL_NAME_WITH_VER}"

  Delete "$SMPROGRAMS\$R9\${PRODUCT_FULL_NAME_WITH_VER}.lnk"
;  Delete "$SMPROGRAMS\$R9\Uninstall ${PRODUCT_FULL_NAME_WITH_VER}.lnk"
; Delete only if empty (last IDEA version is uninstalled)
  RMDir  "$SMPROGRAMS\$R9"

  Delete "$DESKTOP\${PRODUCT_FULL_NAME_WITH_VER}.lnk"
  Delete "$DESKTOP\${PRODUCT_FULL_NAME_WITH_VER}(64).lnk"

registry:
  StrCpy $5 "Software\${MANUFACTURER}"
  StrCmp "${ASSOCIATION}" "NoAssociation" finish_uninstall
  push "${ASSOCIATION}"
loop:
  call un.SplitStr
  Pop $0
  StrCmp $0 "" finish_uninstall
  StrCpy $1 $0
  StrCpy $2 "backup_val"
  Call un.ReturnBackupRegValue
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
  StrCpy $0 "HKCR"
  StrCpy $1 "${PRODUCT_PATHS_SELECTOR}"
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
