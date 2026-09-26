### Module `intellij.platform.backend`

This is a root module in the hierarchy of modules which implement heavy backend functionality of the platform. 
Modules which depend on this module can be loaded by a regular monolith IDE and by an IDE running as a backend in the remote development
scenario. They won't be loaded by an IDE running in frontend mode (JetBrains Client).
