!include "TextFunc.nsh"

var logFile

!macro INST_UNINST_LOGGING_SWITCH un
  Function ${un}createLog
    Push $R0
    Push $R1
    Push $R2
    Push $0
    Push $1
    Push $2
    Push $3
    Push $4
    Push $5
    Push $6
    ${GetParameters} $R0
    ClearErrors
    ${GetOptions} $R0 /LOG= $R1
    StrCpy $logFile $R1
    IfErrors no_log
    FileOpen $R2 $logFile w
   ${GetTime} "" "L" $0 $1 $2 $3 $4 $5 $6
    FileWrite $R2 "--- $0.$1.$2($3) $4:$5:$6 --- $\r"
    FileClose $R2
    Goto done
no_log:
    StrCpy $logFile ""
    ClearErrors
done:
    Pop $6
    Pop $5
    Pop $4
    Pop $3
    Pop $2
    Pop $1
    Pop $0
    Pop $R2
    Pop $R1
    Pop $R0
  FunctionEnd
!macroend

!insertmacro INST_UNINST_LOGGING_SWITCH ""
!insertmacro INST_UNINST_LOGGING_SWITCH "un."

!define LogText "!insertmacro LogTextMacro"
!macro LogTextMacro INPUT_TEXT
  !define UniqueID ${__LINE__}
    StrCmp $logFile "" no_log_${UniqueID} 0
      Push $R2
      FileOpen $R2 $logFile a
      FileSeek $R2 0 END
      FileWrite $R2 "${INPUT_TEXT} $\r"
      FileClose $R2
      Pop $R2
no_log_${UniqueID}:
  !undef UniqueID
!macroend