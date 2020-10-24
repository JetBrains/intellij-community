#include "COM_guard.h"  // COMGuard
#include "COM_errors.h"
#include "jump_task.h"
#include "jump_item.h"
#include "jump_list.h"
#include "application.h"
#include <iostream>     // std::clog
#include <cassert>      // assert


namespace ui = intellij::ui::win;


int main(/*int argc, char* argv[]*/)
{
    std::string s;

    try
    {
        const ui::COMGuard comInitializer{
            COINIT_APARTMENTTHREADED | COINIT_DISABLE_OLE1DDE | COINIT_SPEED_OVER_MEMORY // NOLINT
        };


        std::cout << "Press Enter to initialize an Application:" << std::flush;
        std::getline(std::cin, s);

        std::clog << "Initializing an Application..." << std::endl;
        ui::Application::getInstance();
        std::wclog << L"Application has been initialized successfully. Id: \""
                   << ui::Application::getInstance().obtainAppUserModelId().value_or(L"nullopt")
                   << L"\"\n" << std::endl;


        std::cout << "Press Enter to remove the Jump List:" << std::flush;
        std::getline(std::cin, s);

        std::clog << "Removing the Jump List..." << std::endl;
        ui::Application::getInstance().deleteJumpList(ui::COM_IS_INITIALIZED_IN_THIS_THREAD);
        std::clog << "The Jump List has been removed successfully.\n" << std::endl;


        std::cout << "Press Enter to clear the Recents:" << std::flush;
        std::getline(std::cin, s);

        std::clog << "Clearing the Recents..." << std::endl;
        ui::Application::getInstance().clearRecentsAndFrequents(ui::COM_IS_INITIALIZED_IN_THIS_THREAD);
        std::clog << "The Recents category has been cleared successfully.\n" << std::endl;


        std::cout << "Press Enter to create User's \"Tasks\":" << std::flush;
        std::getline(std::cin, s);

        std::clog << "Creating User's \"Tasks\"..." << std::endl;
        auto userJumpTask1 = ui::JumpTask::Builder{
                L"C:\\Separated\\Soft\\JetBrains\\IntellijIDEA203.4449.2\\bin\\idea64.exe",
                L"Open Intellij IDEA"
            }//.setTasksApplicationArguments(L"")
             .setTasksDescription(L"Run IDEA 64-bit launcher")
             .buildTask(ui::COM_IS_INITIALIZED_IN_THIS_THREAD);
        auto userJumpTask2 = ui::JumpTask::Builder{
                L"notepad.exe",
                L"Open MyDocument.txt"
            }.setTasksApplicationWorkingDirectory(L"C:\\Separated")
             .setTasksApplicationArguments(L"MyDocument.txt")
             .setTasksDescription(L"Open MyDocument.txt via notepad.exe")
             .buildTask(ui::COM_IS_INITIALIZED_IN_THIS_THREAD);
        std::clog << "User's \"Tasks\" have been created successfully.\n" << std::endl;


        std::cout << "Press Enter to create tasks for custom categories:" << std::flush;
        std::getline(std::cin, s);

        std::clog << "Creating tasks for Custom Category 1..." << std::endl;
        auto cc1task1 = ui::JumpTask::Builder{
                L"explorer.exe",
                L"Program Files x86"
            }.setTasksApplicationArguments(L"\"C:\\Program Files (x86)\"")
            .setTasksDescription(L"Open Program Files x86")
            .buildTask(ui::COM_IS_INITIALIZED_IN_THIS_THREAD);
        auto cc1task2 = ui::JumpTask::Builder{
                L"explorer.exe",
                L"Program Files"
            }.setTasksApplicationArguments(L"\"C:\\Program Files\"")
            .setTasksDescription(L"Open Program Files")
            .buildTask(ui::COM_IS_INITIALIZED_IN_THIS_THREAD);
        std::clog << "The tasks for Custom Category 1 have been created successfully.\n" << std::endl;


        std::clog << "Creating tasks for Custom Category 2..." << std::endl;
        auto cc2task1 = ui::JumpTask::Builder{
                L"explorer.exe",
                L"Windows"
            }.setTasksApplicationArguments(L"\"C:\\Windows\\\"")
             .setTasksDescription(L"Open Windows")
             .buildTask(ui::COM_IS_INITIALIZED_IN_THIS_THREAD);
        auto cc2task2 = ui::JumpTask::Builder{
                L"explorer.exe",
                L"Users"
            }.setTasksApplicationArguments(L"\"C:\\Users\"")
             .setTasksDescription(L"Open Users")
             .buildTask(ui::COM_IS_INITIALIZED_IN_THIS_THREAD);
        std::clog << "The tasks for Custom Category 2 have been created successfully.\n" << std::endl;


        std::cout << "Press Enter to create a JumpList:" << std::flush;
        std::getline(std::cin, s);

        std::clog << "Creating a JumpList..." << std::endl;
        ui::JumpList jumpList;
        jumpList.setRecentCategoryVisible(false);
        jumpList.setFrequentCategoryVisible(false);
        jumpList.appendToUserTasks(std::move(userJumpTask1), ui::COM_IS_INITIALIZED_IN_THIS_THREAD);
        jumpList.appendToUserTasks(std::move(userJumpTask2), ui::COM_IS_INITIALIZED_IN_THIS_THREAD);
        jumpList.appendToCustomCategory(L"Custom Category 1", std::move(cc1task1), ui::COM_IS_INITIALIZED_IN_THIS_THREAD);
        jumpList.appendToCustomCategory(L"Custom Category 1", std::move(cc1task2), ui::COM_IS_INITIALIZED_IN_THIS_THREAD);
        jumpList.appendToCustomCategory(L"Custom Category 2", std::move(cc2task1), ui::COM_IS_INITIALIZED_IN_THIS_THREAD);
        jumpList.appendToCustomCategory(L"Custom Category 2", std::move(cc2task2), ui::COM_IS_INITIALIZED_IN_THIS_THREAD);
        std::clog << "JumpList has been created successfully.\n" << std::endl;


        std::cout << "Press Enter to set the JumpList to the Application:" << std::flush;
        std::getline(std::cin, s);

        std::clog << "Setting the JumpList to the Application..." << std::endl;
        ui::Application::getInstance().setJumpList(jumpList, ui::COM_IS_INITIALIZED_IN_THIS_THREAD);
        std::clog << "The JumpList has been set to the Application successfully.\n" << std::endl;


        std::cout << "Press Enter to set Recent documents:" << std::flush;
        std::getline(std::cin, s);

        std::clog << "Setting Recent documents..." << std::endl;
        ui::Application::getInstance().registerRecentlyUsed(L"C:\\Separated\\MyDocument3.txt");
        ui::Application::getInstance().registerRecentlyUsed(L"C:\\Separated\\MyDocument4.txt");
        ui::Application::getInstance().registerRecentlyUsed(L"C:\\Separated\\MyDocument5.txt");
        std::clog << "Recent documents have been set successfully.\n" << std::endl;


        std::cout << "Press Enter to remove the Jump List:" << std::flush;
        std::getline(std::cin, s);

        std::clog << "Removing the Jump List..." << std::endl;
        ui::Application::getInstance().deleteJumpList(ui::COM_IS_INITIALIZED_IN_THIS_THREAD);
        std::clog << "The Jump List has been removed successfully.\n" << std::endl;
    }
    catch(const std::system_error& err)
    {
        std::cerr << "Caught std::system_error with code " << err.code()
                  << " meaning \"" << err.what() << "\"\n" << std::endl;
    }
    catch (const std::exception& err)
    {
        std::cerr << err.what() << "\n" << std::endl;
    }

    std::cout << "Press Enter to exit:" << std::flush;
    std::getline(std::cin, s);

    return 0;
}


#if 0
// HelloWindowsDesktop.cpp
// compile with: /D_UNICODE /DUNICODE /DWIN32 /D_WINDOWS /c

#include <windows.h>
#include <cstdlib>
#include <cstring>
#include <tchar.h>

// Global variables

// The main window class name.
static TCHAR szWindowClass[] = _T("DesktopApp");

// The string that appears in the application's title bar.
static TCHAR szTitle[] = _T("Windows Desktop Guided Tour Application");

HINSTANCE hInst;

// Forward declarations of functions included in this code module:
LRESULT CALLBACK WndProc(HWND, UINT, WPARAM, LPARAM);

int CALLBACK WinMain(
    [[maybe_unused]] _In_ HINSTANCE hInstance,
    [[maybe_unused]] _In_opt_ HINSTANCE hPrevInstance,
    [[maybe_unused]] _In_ LPSTR     lpCmdLine,
    [[maybe_unused]] _In_ int       nCmdShow)
{
    try
    {
        const auto comInitializer = ui::COMGuard(
            COINIT_APARTMENTTHREADED | COINIT_DISABLE_OLE1DDE | COINIT_SPEED_OVER_MEMORY // NOLINT
        );

        std::clog << "Initializing an Application..." << std::endl;
        ui::Application::getInstance();
        std::wclog << L"Application has been initialized successfully. Id: \""
                   << ui::Application::getInstance().obtainAppUserModelId().value_or(L"nullopt")
                   << L"\"\n" << std::endl;


        std::clog << "Removing the Jump List..." << std::endl;
        ui::Application::getInstance().deleteJumpList();
        std::clog << "The Jump List has been removed successfully.\n" << std::endl;


        std::clog << "Clearing the Recents..." << std::endl;
        ui::Application::getInstance().clearRecentsAndFrequents();
        std::clog << "The Recents category has been cleared successfully.\n" << std::endl;


        std::clog << "Creating User's \"Tasks\"..." << std::endl;
        auto userJumpTask1 = ui::JumpTask::Builder{
                comInitializer,
                L"C:\\Separated\\Soft\\JetBrains\\IntellijIDEA203.4449.2\\bin\\idea64.exe",
                L"Open Intellij IDEA"
        }
                //.setTasksApplicationArguments(L"")
                .setTasksDescription(L"Run IDEA 64-bit launcher")
                .buildTask();
        auto userJumpTask2 = ui::JumpTask::Builder{
                comInitializer,
                L"notepad.exe",
                L"Open MyDocument.txt"
        }
                .setTasksApplicationWorkingDirectory(L"C:\\Separated")
                .setTasksApplicationArguments(L"MyDocument.txt")
                .setTasksDescription(L"Open MyDocument.txt via notepad.exe")
                .buildTask();
        std::clog << "User's \"Tasks\" have been created successfully.\n" << std::endl;


        std::clog << "Creating a JumpList..." << std::endl;
        ui::JumpList jumpList;
        jumpList.setRecentCategoryVisible(false);
        jumpList.appendToUserTasks(std::move(userJumpTask1));
        jumpList.appendToUserTasks(std::move(userJumpTask2));
        std::clog << "JumpList has been created successfully.\n" << std::endl;


        std::clog << "Setting the JumpList to the Application..." << std::endl;
        ui::Application::getInstance().setJumpList(jumpList);
        std::clog << "The JumpList has been set to the Application successfully.\n" << std::endl;


        std::clog << "Setting Recents documents..." << std::endl;
        ui::Application::getInstance().registerRecentlyUsed(L"C:\\Separated\\MyDocument3.txt");
        ui::Application::getInstance().registerRecentlyUsed(L"C:\\Separated\\MyDocument4.txt");
        ui::Application::getInstance().registerRecentlyUsed(L"C:\\Separated\\MyDocument5.txt");
        std::clog << "Recents documents have been set successfully.\n" << std::endl;


//        std::clog << "Removing the Jump List..." << std::endl;
//        ui::Application::getInstance().deleteJumpList();
//        std::clog << "The Jump List has been removed successfully.\n" << std::endl;


//        std::cout << "Press Enter to create Recents JumpItems:" << std::flush;
//        std::getline(std::cin, s);
//
//        std::clog << "Creating Recents JumpItems..." << std::endl;
//        ui::JumpItem recentJumpItem1("C:\\Separated\\MyDocument2.txt");
//        std::clog << "Recent JumpItems have been created successfully.\n" << std::endl;
//
//
//        std::cout << "Press Enter to set Recents JumpItems:" << std::flush;
//        std::getline(std::cin, s);
//
//        std::clog << "Setting Recents JumpItems..." << std::endl;
//        ui::Application::getInstance().registerRecentlyUsed(recentJumpItem1);
//        std::clog << "Recents JumpItems have been set successfully.\n" << std::endl;
    }
    catch(const std::system_error& err)
    {
        std::cerr << "Caught std::system_error with code " << err.code()
                  << " meaning \"" << err.what() << "\"\n" << std::endl;
    }
    catch (const std::exception& err)
    {
        std::cerr << err.what() << "\n" << std::endl;
    }

    WNDCLASSEX wcex;

    wcex.cbSize = sizeof(WNDCLASSEX);
    wcex.style          = CS_HREDRAW | CS_VREDRAW;
    wcex.lpfnWndProc    = WndProc;
    wcex.cbClsExtra     = 0;
    wcex.cbWndExtra     = 0;
    wcex.hInstance      = hInstance;
    wcex.hIcon          = LoadIcon(hInstance, IDI_APPLICATION);
    wcex.hCursor        = LoadCursor(NULL, IDC_ARROW);
    wcex.hbrBackground  = (HBRUSH)(COLOR_WINDOW+1);
    wcex.lpszMenuName   = NULL;
    wcex.lpszClassName  = szWindowClass;
    wcex.hIconSm        = LoadIcon(wcex.hInstance, IDI_APPLICATION);

    if (!RegisterClassEx(&wcex))
    {
        MessageBox(NULL,
                   _T("Call to RegisterClassEx failed!"),
                   _T("Windows Desktop Guided Tour"),
                   NULL);

        return 1;
    }

    // Store instance handle in our global variable
    hInst = hInstance;

    // The parameters to CreateWindow explained:
    // szWindowClass: the name of the application
    // szTitle: the text that appears in the title bar
    // WS_OVERLAPPEDWINDOW: the type of window to create
    // CW_USEDEFAULT, CW_USEDEFAULT: initial position (x, y)
    // 500, 100: initial size (width, length)
    // NULL: the parent of this window
    // NULL: this application does not have a menu bar
    // hInstance: the first parameter from WinMain
    // NULL: not used in this application
    HWND hWnd = CreateWindow(
            szWindowClass,
            szTitle,
            WS_OVERLAPPEDWINDOW,
            CW_USEDEFAULT, CW_USEDEFAULT,
            500, 100,
            NULL,
            NULL,
            hInstance,
            NULL
    );

    if (!hWnd)
    {
        MessageBox(NULL,
                   _T("Call to CreateWindow failed!"),
                   _T("Windows Desktop Guided Tour"),
                   NULL);

        return 1;
    }

    // The parameters to ShowWindow explained:
    // hWnd: the value returned from CreateWindow
    // nCmdShow: the fourth parameter from WinMain
    ShowWindow(hWnd, nCmdShow);
    UpdateWindow(hWnd);

    // Main message loop:
    MSG msg;
    while (GetMessage(&msg, NULL, 0, 0))
    {
        TranslateMessage(&msg);
        DispatchMessage(&msg);
    }

    return (int) msg.wParam;
}

//  FUNCTION: WndProc(HWND, UINT, WPARAM, LPARAM)
//
//  PURPOSE:  Processes messages for the main window.
//
//  WM_PAINT    - Paint the main window
//  WM_DESTROY  - post a quit message and return
LRESULT CALLBACK WndProc(HWND hWnd, UINT message, WPARAM wParam, LPARAM lParam)
{
    PAINTSTRUCT ps;
    HDC hdc;
    TCHAR greeting[] = _T("Hello, Windows desktop!");

    switch (message)
    {
        case WM_PAINT:
            hdc = BeginPaint(hWnd, &ps);

            // Here your application is laid out.
            // For this introduction, we just print out "Hello, Windows desktop!"
            // in the top left corner.
            TextOut(hdc,
                    5, 5,
                    greeting, static_cast<int>(_tcslen(greeting)));
            // End application-specific layout section.

            EndPaint(hWnd, &ps);
            break;
        case WM_DESTROY:
            PostQuitMessage(0);
            break;
        default:
            return DefWindowProc(hWnd, message, wParam, lParam);
            break;
    }

    return 0;
}

#endif // 0
