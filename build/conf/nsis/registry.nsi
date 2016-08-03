
!macro INST_UNINST_REGISTRY_SWITCH un
; -----------------------------------------------------------------------------
; OMDeleteRegKey
; $0 - root_key ("HKCU" | "HKLM" | "HKCR")
; $1 - subkey
Function ${un}OMDeleteRegKey
  ClearErrors
  StrCmp $0 "HKCU" hkcu
  StrCmp $0 "HKLM" hklm
  StrCmp $0 "HKCR" hkcr
  hkcu:
    DeleteRegKey HKCU $1
    goto done
  hklm:  
    DeleteRegKey HKLM $1
    goto done
  hkcr:	
    DeleteRegKey HKCR $1
done:
FunctionEnd

; -----------------------------------------------------------------------------
; OMDeleteRegKeyIfEmpty
; $0 - root_key ("HKCU" | "HKLM" | "HKCR")
; $1 - subkey
Function ${un}OMDeleteRegKeyIfEmpty
  ClearErrors
  StrCmp $0 "HKCU" hkcu
  StrCmp $0 "HKLM" hklm
  StrCmp $0 "HKCR" hkcr
  hkcu:
    DeleteRegKey /ifempty HKCU $1
    goto done
  hklm:  
    DeleteRegKey /ifempty HKLM $1
    goto done
  hkcr:	
    DeleteRegKey /ifempty HKCR $1
done:
FunctionEnd

; -----------------------------------------------------------------------------
; OMDeleteRegValue
; $0 - root_key ("HKCU" | "HKLM" | "HKCR")
; $1 - subkey
; $2 - value name
Function ${un}OMDeleteRegValue
  ClearErrors
  StrCmp $0 "HKCU" hkcu
  StrCmp $0 "HKLM" hklm
  StrCmp $0 "HKCR" hkcr
  hkcu:
    DeleteRegValue HKCU $1 $2
    goto done
  hklm:  
    DeleteRegValue HKLM $1 $2
    goto done
  hkcr:	
    DeleteRegValue HKCR $1 $2
done:
FunctionEnd

; -----------------------------------------------------------------------------
; OMReadRegStr
; $0 - root_key ("HKCU" | "HKLM" | "HKCR")
; $1 - subkey
; $2 - value name
; $3 - result
Function ${un}OMReadRegStr
  ClearErrors
  StrCmp $0 "HKCU" hkcu
  StrCmp $0 "HKLM" hklm
  StrCmp $0 "HKCR" hkcr
  hkcu:
    ReadRegStr $3 HKCU $1 $2
    goto done
  hklm:  
    ReadRegStr $3 HKLM $1 $2
    goto done
  hkcr:	
    ReadRegStr $3 HKCR $1 $2
done:
FunctionEnd

; -----------------------------------------------------------------------------
; OMEnumRegKey
; $0 - root_key ("HKCU" | "HKLM" | "HKCR")
; $1 - subkey
; $4 - index
; $3 - result
Function ${un}OMEnumRegKey
  ClearErrors
  StrCmp $0 "HKCU" hkcu
  StrCmp $0 "HKLM" hklm
  StrCmp $0 "HKCR" hkcr
  hkcu:
    EnumRegKey $3 HKCU $1 $4
    goto done
  hklm:  
    EnumRegKey $3 HKLM $1 $4
    goto done
  hkcr:  
    EnumRegKey $3 HKCR $1 $4
done:
FunctionEnd

; -----------------------------------------------------------------------------
; OMEnumRegValue
; $0 - root_key ("HKCU" | "HKLM" | "HKCR")
; $1 - subkey
; $4 - index
; $3 - result
Function ${un}OMEnumRegValue
  ClearErrors
  StrCmp $0 "HKCU" hkcu
  StrCmp $0 "HKLM" hklm
  StrCmp $0 "HKCR" hkcr
  hkcu:
    EnumRegValue $3 HKCU $1 $4
    goto done
  hklm:
    EnumRegValue $3 HKLM $1 $4
    goto done
  hkcr:
    EnumRegValue $3 HKCR $1 $4
done:
FunctionEnd

; -----------------------------------------------------------------------------
; OMWriteRegStr
; $0 - root_key ("HKCU" | "HKLM" | "HKCR")
; $1 - subkey
; $2 - value name
; $3 - value
Function ${un}OMWriteRegStr
  ClearErrors
  StrCmp $0 "HKCU" hkcu
  StrCmp $0 "HKLM" hklm
  StrCmp $0 "HKCR" hkcr
  hkcu:
    WriteRegStr HKCU $1 $2 $3
    goto done
  hklm:  
    WriteRegStr HKLM $1 $2 $3
    goto done
  hkcr:	
    WriteRegStr HKCR $1 $2 $3
done:
FunctionEnd


!macroend

!insertmacro INST_UNINST_REGISTRY_SWITCH ""
!insertmacro INST_UNINST_REGISTRY_SWITCH "un."