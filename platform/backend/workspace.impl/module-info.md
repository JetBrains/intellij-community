This module contains WSM "bindings" for IDE, i.e. the code that integrates WSM into IDE:
startup activities that load initial state, different hooks to broadcast notifications or 
react on different actions like "save project".

This module is backend-only module and must have no dependencies on "frontend" modules (neither 
should it be referenced from any frontend module).

Just like everywhere in the platform, this "impl" module contains implementation details. 
It must not be referenced from other "api" modules. Most likely, it should not be referenced
from any other "impl" module either. If you need compile time dependency on this module, probably
you are missing some API - please consider improving the API instead of depending on implementation.

It is fine to have "runtime" dependency on this module from other "main" modules.