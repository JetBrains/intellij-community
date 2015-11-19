/*
Module : HookImportFunction.cpp
Purpose: Defines the implementation for code to hook a call to any imported Win32 SDK
Created: PJN / 23-10-1999
History: PJN / 01-01-2001 1. Now includes copyright message in the source code and documentation.
                          2. Fixed an access violation in where I was getting the name of the import
                          function but not checking for failure.
                          3. Fixed a compiler error where I was incorrectly casting to a PDWORD instead
                          of a DWORD
         PJN / 20-04-2002 1. Fixed a potential infinite loop in HookImportFunctionByName. Thanks to
                          David Defoort for spotting this problem.

Copyright (c) 1996 - 2002 by PJ Naughter.  (Web: www.naughter.com, Email: pjna@naughter.com)

All rights reserved.

Copyright / Usage Details:

You are allowed to include the source code in any product (commercial, shareware, freeware or otherwise) 
when your product is released in binary form. You are allowed to modify the source code in any way you want 
except you cannot modify the copyright details at the top of each module. If you want to distribute source 
code with your application, then you are only allowed to distribute versions released by the author. This is 
to maintain a single distribution point for the source code. 

*/


////////////////// Includes ////////////////////////////////////

#include <windows.h>
#include "HookImportFunction.h"

#define ASSERT(e)
#define VERIFY(e) e
#define TRACE0(s) OutputDebugString(s)
#define _T(s) s

////////////////// Defines / Locals ////////////////////////////

#ifdef _DEBUG
  #define new DEBUG_NEW
  #undef THIS_FILE
  static char THIS_FILE[] = __FILE__;
#endif

#define MakePtr(cast, ptr, AddValue) (cast)((DWORD)(ptr)+(DWORD)(AddValue))

BOOL IsNT();



////////////////// Implementation //////////////////////////////

BOOL HookImportFunctionsByName(HMODULE hModule, LPCSTR szImportMod, UINT uiCount, 
                               LPHOOKFUNCDESC paHookArray, PROC* paOrigFuncs, UINT* puiHooked)
{
  // Double check the parameters.
  ASSERT(szImportMod);
  ASSERT(uiCount);
  ASSERT(!IsBadReadPtr(paHookArray, sizeof(HOOKFUNCDESC)*uiCount));

#ifdef _DEBUG
  if (paOrigFuncs)
    ASSERT(!IsBadWritePtr(paOrigFuncs, sizeof(PROC)*uiCount));
  if (puiHooked)
    ASSERT(!IsBadWritePtr(puiHooked, sizeof(UINT)));

  //Check each function name in the hook array.
  for (UINT i = 0; i<uiCount; i++)
  {
    ASSERT(paHookArray[i].szFunc);
    ASSERT(*paHookArray[i].szFunc != _T('\0'));

    //If the proc is not NULL, then it is checked.
    if (paHookArray[i].pProc)
      ASSERT(!IsBadCodePtr(paHookArray[i].pProc));
  }
#endif

  //Do the parameter validation for real.
  if (uiCount == 0 || szImportMod == NULL || IsBadReadPtr(paHookArray, sizeof(HOOKFUNCDESC)* uiCount))
  {
    ASSERT(FALSE);
    SetLastErrorEx(ERROR_INVALID_ADDRESS, SLE_ERROR);
    return FALSE;
  }

  if (paOrigFuncs && IsBadWritePtr(paOrigFuncs, sizeof(PROC)*uiCount))
  {
    ASSERT(FALSE);
    SetLastErrorEx(ERROR_INVALID_ADDRESS, SLE_ERROR);
    return FALSE;
  }

  if (puiHooked && IsBadWritePtr(puiHooked, sizeof(UINT)))
  {
    ASSERT(FALSE);
    SetLastErrorEx(ERROR_INVALID_ADDRESS, SLE_ERROR );
    return FALSE;
  }

  //Is this a system DLL, which Windows95 will not let you patch
  //since it is above the 2GB line?
  if (!IsNT() && ((DWORD)hModule >= 0x80000000))
  {
    #ifdef _DEBUG
    CString sMsg;
    sMsg.Format(_T("Could not hook module %x because we are on Win9x and it is in shared memory\n"), hModule);
    OutputDebugString(sMsg);
    #endif
    SetLastErrorEx(ERROR_INVALID_HANDLE, SLE_ERROR);
    return FALSE;
  }

  //TODO TODO
  // Should each item in the hook array be checked in release builds?

  if (puiHooked)
    *puiHooked = 0; //Set the number of functions hooked to zero.

  //Get the specific import descriptor.
  PIMAGE_IMPORT_DESCRIPTOR pImportDesc = GetNamedImportDescriptor(hModule, szImportMod);
  if (NULL == pImportDesc)
    return FALSE; // The requested module was not imported.

  HINSTANCE hImportMod = GetModuleHandle(szImportMod);
  if (NULL == hImportMod)
  {
    ASSERT(FALSE);
    SetLastErrorEx(ERROR_HOOK_NEEDS_HMOD, SLE_ERROR);
    return FALSE; // The requested module was not available.
  }

  //Set all the values in paOrigFuncs to NULL.
  if (NULL != paOrigFuncs)
    memset(paOrigFuncs, NULL, sizeof(PROC)*uiCount);

  //Get the original thunk information for this DLL.  I cannot use
  // the thunk information stored in the pImportDesc->FirstThunk
  // because the that is the array that the loader
  // has already bashed to fix up all the imports. 
  // This pointer gives us acess to the function names.
  PIMAGE_THUNK_DATA pOrigThunk = MakePtr(PIMAGE_THUNK_DATA, hModule, pImportDesc->OriginalFirstThunk);

  //Get the array pointed to by the pImportDesc->FirstThunk. 
  // This is where I will do the actual bash.
  PIMAGE_THUNK_DATA pRealThunk = MakePtr(PIMAGE_THUNK_DATA, hModule, pImportDesc->FirstThunk);

  //Loop through and look for the one that matches the name.
  for (; NULL != pOrigThunk->u1.Function;
      // Increment both tables.
      pOrigThunk++, pRealThunk++)
  {
    //Only look at those that are imported by name, not ordinal.
    if (IMAGE_ORDINAL_FLAG == (IMAGE_ORDINAL_FLAG & pOrigThunk->u1.Ordinal))
      continue;

    //Look get the name of this imported function.
    PIMAGE_IMPORT_BY_NAME pByName = MakePtr(PIMAGE_IMPORT_BY_NAME, hModule, pOrigThunk->u1.AddressOfData);

    if (IsBadReadPtr(pByName, MAX_PATH+4))
    {
      SetLastErrorEx(ERROR_INVALID_ADDRESS, SLE_ERROR);
      continue;
    }

    //If the name starts with NULL, then just skip to next.
    if (_T('\0') == pByName->Name[0])
      continue;

    //Determines if we do the hook.
    BOOL bDoHook = FALSE;

    //TODO {
    // Might want to consider bsearch here.
    //TODO }
    //See if the particular function name is in the import
    // list.  It might be good to consider requiring the
    // paHookArray to be in sorted order so bsearch could be
    // used so the lookup will be faster.  However, the size of
    // uiCount coming into this function should be rather small
    // but it is called for each function imported by szImportMod.
    UINT i;
    for (i = 0; i<uiCount; i++)
    {
      if ((paHookArray[i].szFunc[0] == pByName->Name[0]) &&
        (strcmpi(paHookArray[i].szFunc, (char*)pByName->Name) == 0))
      {
        //If the proc is NULL, kick out, otherwise
        // go ahead and hook it.
        if (paHookArray[i].pProc)
          bDoHook = TRUE;
        break;
      }
    }

    if (FALSE == bDoHook)
      continue;

    // I found it.  Now I need to change the protection to
    //  writable before I do the blast.  Note that I am now
    //  blasting into the real thunk area!
    MEMORY_BASIC_INFORMATION mbi_thunk;
    VirtualQuery(pRealThunk, &mbi_thunk, sizeof(MEMORY_BASIC_INFORMATION));
    VERIFY(VirtualProtect(mbi_thunk.BaseAddress, mbi_thunk.RegionSize, PAGE_READWRITE, &mbi_thunk.Protect));

    // Get fast/simple pointer
    PROC* pFunction = (PROC*) &(pRealThunk->u1.Function);
    if (*pFunction == paHookArray[i].pProc)
    {
      SetLastErrorEx(ERROR_ALREADY_INITIALIZED, SLE_ERROR);
      return FALSE;
    }
    if (IsBadCodePtr(*pFunction))
    {
      ASSERT(FALSE);
      SetLastErrorEx(ERROR_INVALID_ADDRESS, SLE_ERROR);
      return FALSE;
    }
    //Save the original address if requested.
    if (NULL != paOrigFuncs)
    {
      if ((DWORD)(*pFunction) < (DWORD)hImportMod && ((DWORD)(0x80000000) > (DWORD)hImportMod))
      {
        ASSERT(FALSE);
        SetLastErrorEx(ERROR_INVALID_ADDRESS, SLE_ERROR);
        return FALSE;
      }
      if (*pFunction != paOrigFuncs[i])
      {
        if (NULL != paOrigFuncs[i])
        {
          if (paHookArray[i].pProc != paOrigFuncs[i])
          {
            ASSERT(FALSE);
            SetLastErrorEx(ERROR_INVALID_ADDRESS, SLE_ERROR);
            return FALSE;
          }
        }
        paOrigFuncs[i] = * pFunction;
      }
    }
    //Do the actual hook.
    *pFunction = paHookArray[i].pProc;

    //Increment the total number hooked.
    if (puiHooked)
      *puiHooked += 1; 

    //Change the protection back to what it was before I blasted.
    DWORD dwOldProtect;
    VERIFY(VirtualProtect(mbi_thunk.BaseAddress, mbi_thunk.RegionSize, mbi_thunk.Protect, &dwOldProtect));
  }
  //All OK, JumpMaster!
  SetLastError(ERROR_SUCCESS);
  return TRUE;
}

PIMAGE_IMPORT_DESCRIPTOR GetNamedImportDescriptor(HMODULE hModule, LPCSTR szImportMod)
{
  //Always check parameters.
  ASSERT(szImportMod);
  ASSERT(hModule);
  if ((szImportMod == NULL) || (hModule == NULL))
  {
    ASSERT(FALSE);
    SetLastErrorEx(ERROR_INVALID_PARAMETER, SLE_ERROR);
    return NULL;
  }

  //Get the Dos header.
  PIMAGE_DOS_HEADER pDOSHeader = (PIMAGE_DOS_HEADER) hModule;

  // Is this the MZ header?
  if (IsBadReadPtr(pDOSHeader, sizeof(IMAGE_DOS_HEADER)) || (pDOSHeader->e_magic != IMAGE_DOS_SIGNATURE))
  {
    #ifdef _DEBUG
    CString sMsg;
    sMsg.Format(_T("Could not find the MZ Header for %x\n"), hModule);
    OutputDebugString(sMsg);
    #endif
    SetLastErrorEx( ERROR_BAD_EXE_FORMAT, SLE_ERROR);
    return NULL;
  }

  // Get the PE header.
  PIMAGE_NT_HEADERS pNTHeader = MakePtr(PIMAGE_NT_HEADERS, pDOSHeader, pDOSHeader->e_lfanew);

  //Is this a real PE image?
  if (IsBadReadPtr(pNTHeader, sizeof(IMAGE_NT_HEADERS)) || (pNTHeader->Signature != IMAGE_NT_SIGNATURE))
  {
    ASSERT(FALSE);
    SetLastErrorEx( ERROR_INVALID_EXE_SIGNATURE, SLE_ERROR);
    return NULL;
  }

  //If there is no imports section, leave now.
  if (pNTHeader->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_IMPORT].VirtualAddress == 0)
    return NULL;

  // Get the pointer to the imports section.
  PIMAGE_IMPORT_DESCRIPTOR pImportDesc = MakePtr(PIMAGE_IMPORT_DESCRIPTOR, pDOSHeader, pNTHeader->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_IMPORT].VirtualAddress);

  //Loop through the import module descriptors looking for the module whose name matches szImportMod.
  while (pImportDesc->Name)
  {
    PSTR szCurrMod = MakePtr(PSTR, pDOSHeader, pImportDesc->Name);
    if (stricmp(szCurrMod, szImportMod) == 0)
      break; // Found it.

    //Look at the next one.
    pImportDesc++;
  }

  //If the name is NULL, then the module is not imported.
  if (pImportDesc->Name == NULL)
    return NULL;

  //All OK, Jumpmaster!
  return pImportDesc;
}

BOOL IsNT()
{
  OSVERSIONINFO stOSVI;
  memset(&stOSVI, NULL, sizeof(OSVERSIONINFO));
  stOSVI.dwOSVersionInfoSize = sizeof(OSVERSIONINFO);

  BOOL bRet = GetVersionEx(&stOSVI);
  ASSERT(TRUE == bRet);
  if (FALSE == bRet)
  {
    TRACE0("GetVersionEx failed!\n");
    return FALSE;
  }

  //Check the version and call the appropriate thing.
  return (VER_PLATFORM_WIN32_NT == stOSVI.dwPlatformId);
}
