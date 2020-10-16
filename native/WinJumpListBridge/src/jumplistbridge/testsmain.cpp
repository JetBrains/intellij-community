#include "COM_guard.h"  // COMGuard
#include "COM_errors.h"
#include "jump_task.h"
#include "winapi.h"     // CoInitializeEx, CoUninitialize
#include <iostream>     // std::clog
#include <cassert>      // assert


namespace ui = intellij::ui::win;


int main(/*int argc, char* argv[]*/)
{
    try
    {
        const auto comInitializer = ui::COMGuard(
            COINIT_APARTMENTTHREADED | COINIT_DISABLE_OLE1DDE | COINIT_SPEED_OVER_MEMORY // NOLINT
        );

        ui::JumpTask::Builder b(comInitializer, "notepad.exe", L"MyDocument.txt");

        auto task = b.setTasksApplicationWorkingDirectory(L"C:\\Separated")
                     .setTasksApplicationArguments(L"C:\\Separated\\MyDocument.txt")
                     .setTasksDescription(L"Open document MyDocument.txt")
                     .buildTask();

        std::clog << "JumpTask created successfully." << std::endl;
    }
    catch(const std::system_error& e)
    {
        std::cerr << "Caught system_error with code " << e.code()
                  << " meaning \"" << e.what() << "\"" << std::endl;
    }
    catch (const std::exception& err)
    {
        std::cerr << err.what() << std::endl;
    }

    return 0;
}
