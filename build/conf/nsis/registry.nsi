
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
  StrCmp $0 "SHCTX" shctx
shctx:
  DeleteRegKey SHCTX $1
  goto done
hkcu:
  DeleteRegKey HKCU $1
  goto done
hklm:
  DeleteRegKey HKLM $1
  goto done
hkcr:
  DeleteRegKey HKCR $1
done:
  ${LogText} "  deleted registry key: $0 $1"
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
  StrCmp $0 "SHCTX" shctx
shctx:
  DeleteRegKey /ifempty SHCTX $1
  goto done
hkcu:
  DeleteRegKey /ifempty HKCU $1
  goto done
hklm:
  DeleteRegKey /ifempty HKLM $1
  goto done
hkcr:
  DeleteRegKey /ifempty HKCR $1
done:
  ${LogText} "  deleted registry key: $0 $1"
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
  StrCmp $0 "SHCTX" shctx
shctx:
  DeleteRegValue SHCTX $1 $2
  goto done
hkcu:
  DeleteRegValue HKCU $1 $2
  goto done
hklm:
  DeleteRegValue HKLM $1 $2
  goto done
hkcr:
  DeleteRegValue HKCR $1 $2
done:
  ${LogText} "  deleted registry value: $0 $1 $2"
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
  StrCmp $0 "SHCTX" shctx
shctx:
  ReadRegStr $3 SHCTX $1 $2
  goto done
hkcu:
  ReadRegStr $3 HKCU $1 $2
  goto done
hklm:
  ReadRegStr $3 HKLM $1 $2
  goto done
hkcr:
  ReadRegStr $3 HKCR $1 $2
done:
  ${LogText} "  read registry string: $0 $1 $2 $3"
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
  ${LogText} "  find registry key: $0 $1 $4 $3"
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
  ${LogText} "  find registry value: $0 $1 $4 $3"
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
  StrCmp $0 "SHCTX" shctx
shctx:
  WriteRegStr SHCTX $1 $2 $3
  goto done
hkcu:
  WriteRegStr HKCU $1 $2 $3
  goto done
hklm:
  WriteRegStr HKLM $1 $2 $3
  goto done
hkcr:
  WriteRegStr HKCR $1 $2 $3
done:
 ${LogText} "  write registry string: $0 $1 $2 $3"
FunctionEnd
!macroend

!insertmacro INST_UNINST_REGISTRY_SWITCH ""
!insertmacro INST_UNINST_REGISTRY_SWITCH "un."