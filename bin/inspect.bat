@echo off
set REQUIRED_IDEA_JVM_ARGS=-Didea.load.plugins.category=inspection
call idea.bat inspect %*
