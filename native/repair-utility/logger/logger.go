package logger

import (
	"errors"
	"io"
	"io/ioutil"
	"log"
	"os"
	"runtime/debug"
)

type RepairLogger struct {
	log     *log.Logger
	logtype string
}

var (
	WarningLogger RepairLogger
	InfoLogger    RepairLogger
	ErrorLogger   RepairLogger
	ConsoleLogger RepairLogger
	FatalLogger   RepairLogger
	DebugLogger   RepairLogger
	DebugEnabled  bool
	LogFile       *os.File
)

func init() {
	dest, err := ioutil.TempDir("", "IntelliJRepairBinary")
	LogFile, err = os.OpenFile(dest+string(os.PathSeparator)+"repair.log", os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0666)
	if err != nil {
		log.Fatal(err)
	}
	mw := io.MultiWriter(os.Stdout, LogFile)
	ConsoleLogger = RepairLogger{log.New(mw, "", 0), "console"}
	InfoLogger = RepairLogger{log.New(mw, "INFO: ", log.Ldate|log.Ltime), "info"}
	DebugLogger = RepairLogger{log.New(LogFile, "DEBUG: ", log.Ldate|log.Ltime|log.Lshortfile), "debug"}
	WarningLogger = RepairLogger{log.New(mw, "WARNING: ", 0), "warning"}
	ErrorLogger = RepairLogger{log.New(mw, "ERROR: ", 0), "error"}
	FatalLogger = RepairLogger{log.New(mw, "FATAL ERROR: ", 0), "fatal"}
	InfoLogger.Printf("Log file %s created", LogFile.Name())
}
func RemoveLogFile() {
	if _, err := os.Open(LogFile.Name()); err == nil {
		_ = os.Remove(LogFile.Name())
	}
}
func WriteToLogOnError(err error, log RepairLogger) {
	if err != nil {
		log.log.Println(err)
	}
}
func ExitOnError(err error) {
	if err != nil {
		FatalLogger.log.Fatal(err)
	}
}
func ExitWithExceptionOnError(err error) {
	if err != nil {
		err = errors.New(string(debug.Stack()) + err.Error())
		FatalLogger.log.Fatal(err)
	}
}

func (l *RepairLogger) Fatal(v ...interface{}) {
	l.log.Fatal(v...)
}
func (l *RepairLogger) Println(v ...interface{}) {
	l.log.Println(v...)
}
func (l *RepairLogger) Printf(format string, v ...interface{}) {
	l.log.Printf(format, v...)
}

func (l *RepairLogger) SetOutput(w io.Writer) {
	l.log.SetOutput(w)
}
