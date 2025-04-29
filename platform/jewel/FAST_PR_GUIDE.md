# Fast PR Guide

In order to get your pull requests merged with any futher complications (inside community repository) it is advised to follow these:

1) Make sure your commits are prefixed with `[jewel] <Jewel YouTrack Id> ` to ensure distinction of commits and ensure automation works.
2) Prefix the PR name with `[jewel]` and ideally with `jewel` label.
3) Avoid creating pull requests from chains of feature branches (branching from feature branches), this is possible, but can cause issues if combined with squashing/amending the commits. If you do this, ensure you properly rebase all of the branches.
4) Ideally squash the commits, this is not a requirement, but beware that your feature branch might became unsuable if we squash the commits as part of the cherry-pick process.

## Adding modules
When adding modules, there is multiple tests ensuring quality of community project. These rules should be folowed in addition to rules above:
1) Ensure that .idea/modules.xml is sorted alphabetically
2) Source roots declared in the module .iml should match the folder structure. Do not add source roots that don't exist and vice versa.
3) In resource folder, file called `<module name>.xml` should be placed that contains the V2 Plugin configuration. For sake of Jewel/platform modules, it is essential to alteast declare module dependencies in this file.
4) Make sure that `.iml` files are not being modified after being changed by the IDE dialogs.

## Adding dependencies
1) Library for new dependency has to be stated inside CommunityLibraryLicenses.kt. If you are adding part of library already added, you still need to add the alternative library name to this file.
2) Alternatively, library used by multiple modules might be added to community/libraries as a separate module containing only the dependency.
3) Try not to use "exported" flag in dependencies.
4) Make sure that dependencies already contained in the platform are not included as transitive dependencies. (same applies if the current module depends on module with this dependency already added.)
