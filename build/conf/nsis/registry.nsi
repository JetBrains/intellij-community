; -----------------------------------------------------------------------------
; OMReadRegStr
; $0 - root_key ("HKCU" | "HKLM")
; $1 - subkey
; $2 - value name
; $3 - result

Function OMReadRegStr
  StrCmp $0 "HKCU" hkcu
    ReadRegStr $3 HKLM $1 $2
    goto done
hkcu:
    ReadRegStr $3 HKCU $1 $2
done:
FunctionEnd

; -----------------------------------------------------------------------------
; OMDeleteRegValue
; $0 - root_key ("HKCU" | "HKLM")
; $1 - subkey
; $2 - value name

Function OMDeleteRegValue
  StrCmp $0 "HKCU" hkcu
    DeleteRegValue HKLM $1 $2
    goto done
hkcu:
    DeleteRegValue HKCU $1 $2
done:
FunctionEnd

; -----------------------------------------------------------------------------
; OMDeleteRegKeyIfEmpty
; $0 - root_key ("HKCU" | "HKLM")
; $1 - subkey

Function OMDeleteRegKeyIfEmpty
  StrCmp $0 "HKCU" hkcu
    DeleteRegKey /ifempty HKLM $1
    goto done
hkcu:
    DeleteRegKey /ifempty HKCU $1
done:
FunctionEnd

; -----------------------------------------------------------------------------
; OMDeleteRegKey
; $0 - root_key ("HKCU" | "HKLM")
; $1 - subkey

Function OMDeleteRegKey
  StrCmp $0 "HKCU" hkcu
    DeleteRegKey /ifempty HKLM $1
    goto done
hkcu:
    DeleteRegKey /ifempty HKCU $1
done:
FunctionEnd


; -----------------------------------------------------------------------------
; OMWriteRegStr
; $0 - root_key ("HKCU" | "HKLM")
; $1 - subkey
; $2 - value name
; $3 - value

Function OMWriteRegStr
  StrCmp $0 "HKCU" hkcu
    WriteRegStr HKLM $1 $2 $3
    goto done
hkcu:
    WriteRegStr HKCU $1 $2 $3
done:
FunctionEnd
