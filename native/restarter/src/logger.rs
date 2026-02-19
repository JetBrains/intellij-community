// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::env;
use std::fs::File;
use std::io::Write;
use std::time::{Instant, SystemTime};

pub struct Logger {
    start: Instant,
    log: Box<dyn Write>
}

impl Logger {
    pub fn init() -> Logger {
        let start = Instant::now();
        let mut log: Box<dyn Write> = if let Ok(path) = env::var("IJ_RESTARTER_LOG") {
            Box::new(File::create(path).unwrap())
        } else {
            Box::new(std::io::stdout())
        };
        let _ = writeln!(log, "============= {}", chrono::DateTime::from(SystemTime::now()));
        Logger { start, log }
    }

    pub fn info(self: &mut Logger, details: &str) {
        self.log("info", details);
    }

    pub fn error(self: &mut Logger, details: &str) {
        self.log("error", details);
    }

    fn log(self: &mut Logger, level: &str, details: &str) {
        let ms = Instant::now().duration_since(self.start).as_millis();
        let result = writeln!(self.log, "{:5} [{:5}] {}", ms, level, details);
        if let Err(e) = result {
            eprintln!("[error] cannot write to the log: {}\n>> {:5} [{:5}] {}", e, ms, level, details);
        }
    }
}
