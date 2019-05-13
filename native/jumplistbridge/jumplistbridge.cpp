/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

 /**
  * @author Denis Fokin
  */

#include "stdafx.h"
#include <windows.h>
#include <Objbase.h>
#include "jumplistbridge.h"
#include <ShObjIdl.h>
#include <Objectarray.h>
#include <Knownfolders.h>
#include <Shlobj.h>
#pragma comment(lib, "Shlwapi.lib")
#include <wchar.h>
#include <propvarutil.h>
#include <propsys.h>
#include <propkey.h>
#include <stdio.h>
#include <comdef.h>

wchar_t * jstowsz(JNIEnv* env, jstring string)
{
	if (string == NULL) {
		return NULL;
	}

	int chCount = env->GetStringLength(string);
	const jchar* javaChars = env->GetStringChars(string, NULL);

	if (javaChars == NULL) {
		return NULL;
	}

	wchar_t* wideString = new wchar_t[chCount+1];
	memcpy(wideString, javaChars, chCount*2);
	wideString[chCount] = 0;
	env->ReleaseStringChars(string, javaChars);
	return wideString;
}

PCWSTR jetBrainsAppId;

/*
* Class:     com_intellij_ui_win_RecentTasks
* Method:    initialize
* Signature: (Ljava/lang/String;)V
*/
JNIEXPORT void JNICALL Java_com_intellij_ui_win_RecentTasks_initialize
	(JNIEnv * jEnv, jclass clz, jstring jAppId) {
		jetBrainsAppId = jstowsz(jEnv, jAppId);
		SetCurrentProcessExplicitAppUserModelID(jetBrainsAppId);
		::CoInitialize(NULL);
}

/*
* Class:     com_intellij_ui_win_RecentTasks
* Method:    clearNative
* Signature: ()V
*/
JNIEXPORT void JNICALL Java_com_intellij_ui_win_RecentTasks_clearNative
	(JNIEnv *, jclass) {

		ICustomDestinationList * pcdl;
		HRESULT hr = CoCreateInstance(CLSID_DestinationList, NULL, CLSCTX_INPROC_SERVER, IID_ICustomDestinationList, (void**)&pcdl);

		if (SUCCEEDED(hr)) {
			pcdl->SetAppID(jetBrainsAppId);

			UINT uMaxSlots;
			IObjectArray *poaRemoved;
			hr = pcdl->BeginList(
				&uMaxSlots,
				IID_PPV_ARGS(&poaRemoved));
			pcdl->DeleteList(jetBrainsAppId);
			hr = pcdl->CommitList();
		}
}

HRESULT _CreateShellLink(PCWSTR pszPath,
						 PCWSTR pszArguments, PCWSTR pszTitle,
						 IShellLink **ppsl)
{
	IShellLink *psl;
	HRESULT hr = CoCreateInstance(
		CLSID_ShellLink,
		NULL,
		CLSCTX_INPROC_SERVER,
		IID_PPV_ARGS(&psl));
	if (SUCCEEDED(hr))
	{
		hr = psl->SetPath(pszPath);
		if (SUCCEEDED(hr))
		{
			hr = psl->SetArguments(pszArguments);
			if (SUCCEEDED(hr))
			{
				IPropertyStore *pps;
				hr = psl->QueryInterface(IID_PPV_ARGS(&pps));
				if (SUCCEEDED(hr))
				{
					PROPVARIANT propvar;
					hr = InitPropVariantFromString(pszTitle, &propvar);
					if (SUCCEEDED(hr))
					{   hr = pps->SetValue(PKEY_Title, propvar);
					if (SUCCEEDED(hr))
					{
						hr = pps->Commit();
						if (SUCCEEDED(hr))
						{
							hr = psl->QueryInterface
								(IID_PPV_ARGS(ppsl));
						}
					}
					PropVariantClear(&propvar);
					}
					pps->Release();
				}
			}
		}
		else
		{
			hr = HRESULT_FROM_WIN32(GetLastError());
		}
		psl->Release();
	}
	return hr;
}

void checkJNISuccess (JNIEnv * jEnv) {
	if (jEnv->ExceptionOccurred()) {
		jEnv->ExceptionDescribe();
	}
}


LPCWSTR getShortPath (LPCWSTR lpszPath) {
    long     length = 0;
    TCHAR*   buffer = NULL;
    length = GetShortPathName(lpszPath, NULL, 0);
    buffer = new TCHAR[length];
    length = GetShortPathName(lpszPath, buffer, length);
    return buffer;

}

/*
* Class:     com_intellij_ui_win_RecentTasks
* Method:    getShortenPath
* Signature: ([Ljava/lang/String;)[Ljava/lang/String;
*/
JNIEXPORT jstring JNICALL Java_com_intellij_ui_win_RecentTasks_getShortenPath
	(JNIEnv * jEnv, jclass c, jstring path)
{

	if (path == NULL) {
		wprintf(L"The object is NULL \n");
	}

	long     length = 0;
    TCHAR*   shortenPath = NULL;
    length = GetShortPathName(jstowsz(jEnv, path), NULL, 0);
    shortenPath = new TCHAR[length];
    GetShortPathName(jstowsz(jEnv, path), shortenPath, length);

	char *mbDcr = 0;

	int mblen = WideCharToMultiByte(CP_ACP, WC_NO_BEST_FIT_CHARS, shortenPath, length, mbDcr, NULL, NULL, NULL);
	mbDcr = new char[mblen + 1];
	WideCharToMultiByte(CP_ACP, WC_NO_BEST_FIT_CHARS, shortenPath, length, mbDcr, length + 1, NULL, NULL);

	jstring jstrBuf = jEnv->NewStringUTF(mbDcr);

	return jstrBuf;
}

/*
* Class:     com_intellij_ui_win_RecentTasks
* Method:    addTasksNativeForCategory
* Signature: (Ljava/lang/String;[Lcom/intellij/ui/win/RecentTasks/Task;)V
*/
JNIEXPORT void JNICALL Java_com_intellij_ui_win_RecentTasks_addTasksNativeForCategory
	(JNIEnv * jEnv, jclass c, jstring cn, jobjectArray linksArray)
{
	LPCWSTR categoryName = (wchar_t*) jEnv->GetStringChars(cn, 0);

	ICustomDestinationList * pcdl;
	HRESULT hr = CoCreateInstance(CLSID_DestinationList, NULL, CLSCTX_INPROC_SERVER, IID_ICustomDestinationList, (void**)&pcdl);

	if (SUCCEEDED(hr)) {
		pcdl->SetAppID(jetBrainsAppId);

		UINT uMaxSlots;
		IObjectArray *poaRemoved;
		hr = pcdl->BeginList(
			&uMaxSlots,
			IID_PPV_ARGS(&poaRemoved));
		if (SUCCEEDED(hr))
		{
			IObjectCollection *poc;
			HRESULT hr = CoCreateInstance(CLSID_EnumerableObjectCollection, NULL, CLSCTX_INPROC, IID_PPV_ARGS(&poc));
			if (SUCCEEDED(hr))
			{
				jint i;
				jint count = jEnv->GetArrayLength(linksArray);
				for (i=0; i < count; i++) {
					jobject linkData = jEnv->GetObjectArrayElement(linksArray, i);
					checkJNISuccess(jEnv);

					jclass cls = jEnv->GetObjectClass(linkData);
					checkJNISuccess(jEnv);

					jfieldID pathFieldId = jEnv->GetFieldID(cls, "path", "Ljava/lang/String;");
					checkJNISuccess(jEnv);

					jfieldID argsFieldId = jEnv->GetFieldID(cls, "args", "Ljava/lang/String;");
					checkJNISuccess(jEnv);

					jfieldID descriptionFieldId = jEnv->GetFieldID(cls, "description", "Ljava/lang/String;");
					checkJNISuccess(jEnv);

					jstring path  = (jstring)jEnv->GetObjectField(linkData, pathFieldId);
					checkJNISuccess(jEnv);

					jstring args  = (jstring)jEnv->GetObjectField(linkData, argsFieldId);
					checkJNISuccess(jEnv);

					jstring description  = (jstring)jEnv->GetObjectField(linkData, descriptionFieldId);
					checkJNISuccess(jEnv);

					IShellLink * psl;
					hr = _CreateShellLink(jstowsz(jEnv, path),
						jstowsz(jEnv, args),
						jstowsz(jEnv, description), &psl);

					if (SUCCEEDED(hr))
					{
						hr = poc->AddObject(psl);
						psl->Release();

					} else {
						jEnv->ThrowNew(jEnv->FindClass("java/lang/Exception"), "Cannot create a shell link");
					}

					jEnv->DeleteLocalRef(linkData);
				}

				pcdl->AppendCategory(categoryName, poc);
			} else {
				jEnv->ThrowNew(jEnv->FindClass("java/lang/Exception"), "Cannot recieve CLSID_EnumerableObjectCollection interface");
			}
			pcdl->CommitList();
			pcdl->Release();
		} else {
			jEnv->ThrowNew(jEnv->FindClass("java/lang/Exception"), "Cannot begin DestinationList");
		}
	} else {
		jEnv->ThrowNew(jEnv->FindClass("java/lang/Exception"), "Cannot recieve CLSID_DestinationList interface");
	}
}
