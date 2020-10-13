# Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

function(enable_target_compile_extra_warnings target)
    if (MSVC)
        target_compile_options(${target}
            PRIVATE /W4          # warnings level
            PRIVATE /WX          # treat all warnings as errors
        )
    else()
        target_compile_options(${target}
            PRIVATE -Wall        # basic set of warnings
            PRIVATE -Wextra      # additional warnings
            PRIVATE -pedantic    # modern C++ inspections
            PRIVATE -Werror      # treat all warnings as errors
        )
    endif()
endfunction()
