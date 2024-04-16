# Why compatibility modules exist

In short, compatibility modules solve the circular dependency problem.

1. `lang-impl` module is loaded with the core classloader, which is a parent of all other module classloaders
=> `lang-impl` cannot depend on module classloaders.
2. Modules `intellij.platform.navbar`, `intellij.platform.navbar.frontend`, `intellij.platform.navbar.backend` 
are still used from within `lang-impl`
=> they have to be in the same classloader as `lang-impl`
=> they cannot be loaded as separate modules.
3. Some navbar-related code depends on `lang-impl`, because we have to keep APIs in `lang-impl`, 
for example, `com.intellij.ide.navigationToolbar.NavBarModelExtension`. 
This code is not used from `lang-impl`, so it was extracted it. These are compatibility modules.

It's not possible to move everything to `intellij.platform.navbar`, `intellij.platform.navbar.frontend`, `intellij.platform.navbar.backend` 
because they would depend on `lang-impl` and `lang-impl` would depend on them.

After there is no more usages of classes from `intellij.platform.navbar`, `intellij.platform.navbar.frontend`, `intellij.platform.navbar.backend`
in `lang-impl`, it's possible to remove the dependency on these modules from `lang-impl`.
After that, they can be removed from the core classloader and become true modules.
After that, the content of compatibility modules can be merged into corresponding navbar modules.
