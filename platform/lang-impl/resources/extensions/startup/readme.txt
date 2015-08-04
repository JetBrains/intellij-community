Scripts contained in this directory are executed on IDE startup.

## Bindings
* 'IDE' binding from the IDE Scripting Console is available
* script output is redirected to the IDE's log

## Execution
* each script is executed exactly once after a project's initialization is complete
* scripts are executed in alphabetical order
* a script engine for a script is looked up by script's extension

## Script Languages
Enabling scripting in your favourite scripting language is easy:
put your language runtime, JSR-223, and required libraries to %IDE_HOME%/lib/