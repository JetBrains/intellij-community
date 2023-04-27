@echo off
for /f %%i in ('git.exe rev-parse --show-toplevel') do set "toplevel=%%~fi"
call "%toplevel%\build\protobuf\getprotoc.bat"
@echo on

protoc -I=. --java_out=..\gen --java_opt=annotate_code javac_remote_proto.proto
