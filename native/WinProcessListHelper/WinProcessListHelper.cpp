
#define _WIN32_DCOM
#include <iostream>
using namespace std;
#include <comdef.h>
#include <Wbemidl.h>
#include <string>
#include <io.h>
#include <fcntl.h>

#pragma comment(lib, "wbemuuid.lib")

void stringReplaceAll(wstring& str, const wstring& oldStr, const wstring& newStr)
{
    int pos = 0;
    while ((pos = str.find(oldStr, pos)) != string::npos)
    {
        str.replace(pos, oldStr.length(), newStr);
        pos += newStr.length();
    }
}

wstring escapeLineBreaks(const wstring& str)
{
    wstring result = str;
    stringReplaceAll(result, L"\\", L"\\\\");
    stringReplaceAll(result, L"\n", L"\\n");
    stringReplaceAll(result, L"\r", L"\\r");
    return result;
}

int main(int argc, char **argv)
{
    _setmode(_fileno(stdout), _O_U8TEXT);

    HRESULT hres;

    // Step 1: --------------------------------------------------
    // Initialize COM. ------------------------------------------

    hres = CoInitializeEx(nullptr, COINIT_MULTITHREADED);
    if (FAILED(hres))
    {
        cout << "Failed to initialize COM library. Error code = 0x"
            << hex << hres << endl;
        return 1;                  // Program has failed.
    }

    // Step 2: --------------------------------------------------
    // Set general COM security levels --------------------------

    hres = CoInitializeSecurity(
        nullptr,
        -1,                          // COM authentication
        nullptr,                        // Authentication services
        nullptr,                        // Reserved
        RPC_C_AUTHN_LEVEL_DEFAULT,   // Default authentication 
        RPC_C_IMP_LEVEL_IMPERSONATE, // Default Impersonation  
        nullptr,                        // Authentication info
        EOAC_NONE,                   // Additional capabilities 
        nullptr                         // Reserved
        );


    if (FAILED(hres))
    {
        cout << "Failed to initialize security. Error code = 0x"
            << hex << hres << endl;
        CoUninitialize();
        return 1;                    // Program has failed.
    }

    // Step 3: ---------------------------------------------------
    // Obtain the initial locator to WMI -------------------------

    IWbemLocator *pLoc = nullptr;

    hres = CoCreateInstance(
        CLSID_WbemLocator,
        nullptr,
        CLSCTX_INPROC_SERVER,
        IID_IWbemLocator, reinterpret_cast<LPVOID *>(&pLoc));

    if (FAILED(hres))
    {
        cout << "Failed to create IWbemLocator object."
            << " Err code = 0x"
            << hex << hres << endl;
        CoUninitialize();
        return 1;                 // Program has failed.
    }

    // Step 4: -----------------------------------------------------
    // Connect to WMI through the IWbemLocator::ConnectServer method

    IWbemServices *pSvc = nullptr;

    // Connect to the root\cimv2 namespace with
    // the current user and obtain pointer pSvc
    // to make IWbemServices calls.
    hres = pLoc->ConnectServer(
        _bstr_t(L"ROOT\\CIMV2"), // Object path of WMI namespace
        nullptr,                    // User name. NULL = current user
        nullptr,                    // User password. NULL = current
        nullptr,                       // Locale. NULL indicates current
        NULL,                    // Security flags.
        nullptr,                       // Authority (for example, Kerberos)
        nullptr,                       // Context object 
        &pSvc                    // pointer to IWbemServices proxy
        );

    if (FAILED(hres))
    {
        cout << "Could not connect. Error code = 0x"
            << hex << hres << endl;
        pLoc->Release();
        CoUninitialize();
        return 1;                // Program has failed.
    }

    //cout << "Connected to ROOT\\CIMV2 WMI namespace" << endl;


    // Step 5: --------------------------------------------------
    // Set security levels on the proxy -------------------------

    hres = CoSetProxyBlanket(
        pSvc,                        // Indicates the proxy to set
        RPC_C_AUTHN_WINNT,           // RPC_C_AUTHN_xxx
        RPC_C_AUTHZ_NONE,            // RPC_C_AUTHZ_xxx
        nullptr,                        // Server principal name 
        RPC_C_AUTHN_LEVEL_CALL,      // RPC_C_AUTHN_LEVEL_xxx 
        RPC_C_IMP_LEVEL_IMPERSONATE, // RPC_C_IMP_LEVEL_xxx
        nullptr,                        // client identity
        EOAC_NONE                    // proxy capabilities 
        );

    if (FAILED(hres))
    {
        cout << "Could not set proxy blanket. Error code = 0x"
            << hex << hres << endl;
        pSvc->Release();
        pLoc->Release();
        CoUninitialize();
        return 1;               // Program has failed.
    }

    // Step 6: --------------------------------------------------
    // Use the IWbemServices pointer to make requests of WMI ----

    // For example, get the name of the operating system
    IEnumWbemClassObject* pEnumerator = nullptr;
    hres = pSvc->ExecQuery(
        bstr_t("WQL"),
        bstr_t("SELECT * FROM Win32_Process"),
        WBEM_FLAG_FORWARD_ONLY | WBEM_FLAG_RETURN_IMMEDIATELY,
        nullptr,
        &pEnumerator);

    if (FAILED(hres))
    {
        cout << "Query for processes failed."
            << " Error code = 0x"
            << hex << hres << endl;
        pSvc->Release();
        pLoc->Release();
        CoUninitialize();
        return 1;               // Program has failed.
    }

    // Step 7: -------------------------------------------------
    // Get the data from the query in step 6 -------------------

    IWbemClassObject *pclsObj = nullptr;
    ULONG uReturn = 0;
    int i = 0;
    while (pEnumerator)
    {
        i++;
        HRESULT hr = pEnumerator->Next(WBEM_INFINITE, 1,
            &pclsObj, &uReturn);

        if (0 == uReturn)
        {
            break;
        }

        VARIANT vtId = { 0 };
        hr = pclsObj->Get(L"ProcessId", 0, &vtId, nullptr, nullptr);
        INT pid = SUCCEEDED(hr) ? vtId.intVal : 0;
        wcout << "pid:" << pid << "\n";
        VARIANT vtParentId = { 0 };
        hr = pclsObj->Get(L"ParentProcessId", 0, &vtParentId, nullptr, nullptr);
        INT parentPid = SUCCEEDED(hr) ? vtParentId.intVal : 0;
        wcout << "parentPid:" << parentPid << "\n";
        VARIANT vtName = { 0 };
        hr = pclsObj->Get(L"Name", 0, &vtName, nullptr, nullptr);
        if (SUCCEEDED(hr) && vtName.bstrVal != nullptr)
            wcout << L"name:" << escapeLineBreaks(vtName.bstrVal) << L"\n";
        else
            wcout << L"name:\n";
        VARIANT vtCmd = { 0 };
        hr = pclsObj->Get(L"CommandLine", 0, &vtCmd, nullptr, nullptr);
        if (SUCCEEDED(hr) && vtCmd.bstrVal != nullptr)
            wcout << L"cmd:" << escapeLineBreaks(vtCmd.bstrVal) << L"\n";
        else
            wcout << L"cmd:\n";
        VariantClear(&vtId);
        VariantClear(&vtName);
        VariantClear(&vtCmd);

        pclsObj->Release();
    }
    // Cleanup
    // ========

    pSvc->Release();
    pLoc->Release();
    pEnumerator->Release();
    CoUninitialize();
    return 0;   // Program successfully completed.
}